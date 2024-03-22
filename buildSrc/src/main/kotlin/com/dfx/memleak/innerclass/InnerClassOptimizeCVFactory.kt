package com.dfx.memleak.innerclass

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode


internal abstract class InnerClassOptimizeCVFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return OptimizeClassNode(nextClassVisitor, classContext.currentClassData)
    }


    // decide whether or not to handle specific class
    override fun isInstrumentable(classData: ClassData): Boolean {
        return true
    }

    private class OptimizeClassNode(val nextVisitor: ClassVisitor, val classData: ClassData) :
        ClassNode(ASM9) {
        override fun visitEnd() {
            if (ActivityInfoContainer.isActivityInnerClass(classData.className)
                && fields.any { it.name == "this\$0" }
            ) {
                println("found an inner anonymous class of activity $outerClass")
                // 移除this$0的字段，新增mWeakActivity字段
                fields.removeIf { it.name == "this\$0" }
                fields.add(
                    FieldNode(
                        ACC_PRIVATE,
                        "mWeakActivity",
                        "Ljava/lang/ref/WeakReference;",
                        null,
                        null
                    )
                )
                val initMethod =
                    methods.find { it.name == "<init>" && it.desc == "(L$outerClass;)V" }
                initMethod?.instructions?.let {
                    it.forEach { node: AbstractInsnNode ->
                        when (node) {
                            is VarInsnNode -> println("VarInSnNode ${node.opcode} ${node.`var`}")
                            is FieldInsnNode -> println("FieldInsnNode ${node.opcode} ${node.name} ${node.desc}")
                            is MethodInsnNode -> println("MethodInsnNode ${node.opcode} ${node.name} ${node.desc}")
                            is LabelNode -> println("LabelNode ${node.label}")
                            else -> println("${node.opcode}")
                        }
                    }

                    // 用弱引用变量的构造方法替换this$0的putField
                    val putField =
                        it.find { node ->
                            node is FieldInsnNode
                                    && node.opcode == PUTFIELD && node.name == "this\$0"
                        }
                    val aload1 = putField?.previous
                    if (putField == null || aload1 == null || aload1 !is VarInsnNode || aload1.`var` != 1) {
                        println("this\$ field doesn't exist or previous instruction is not aload_1")
                        return@let
                    }

                    var preNode = insertMethodNode(
                        it,
                        putField,
                        TypeInsnNode(NEW, "java/lang/ref/WeakReference")
                    )

                    it.remove(putField)
                    it.remove(aload1)

                    preNode = insertMethodNode(it, preNode, InsnNode(DUP))
                    preNode = insertMethodNode(it, preNode, VarInsnNode(ALOAD, 1))
                    preNode = insertMethodNode(
                        it, preNode, MethodInsnNode(
                            INVOKESPECIAL,
                            "java/lang/ref/WeakReference",
                            "<init>",
                            "(Ljava/lang/Object;)V",
                            false
                        )
                    )
                    insertMethodNode(
                        it, preNode, FieldInsnNode(
                            PUTFIELD,
                            this@OptimizeClassNode.name,
                            "mWeakActivity",
                            "Ljava/lang/ref/WeakReference;"
                        )
                    )
                }

                // 对匿名内部类中所有涉及this$0访问的地方都修改为访问mWeakActivity， 包括构造函数
                methods.forEach { method ->
                    with(method.instructions) {

                        // todo: 实际只在第一处访问this$0的地方判空就好，如果空直接返回，后面再访问就不用再判空了
                        val getFieldNodes =
                            filter { it is FieldInsnNode && it.name == "this\$0" && it.opcode == GETFIELD }
                        getFieldNodes.forEach { node ->
                            var preNode = insertMethodNode(
                                this, node, FieldInsnNode(
                                    GETFIELD, this@OptimizeClassNode.name,
                                    "mWeakActivity",
                                    "Ljava/lang/ref/WeakReference;"
                                )
                            )

                            remove(node)

                            preNode = insertMethodNode(
                                this,
                                preNode,
                                MethodInsnNode(
                                    INVOKEVIRTUAL,
                                    "java/lang/ref/WeakReference",
                                    "get",
                                    "()Ljava/lang/Object;"
                                )
                            )
                            preNode = insertMethodNode(
                                this,
                                preNode,
                                TypeInsnNode(
                                    CHECKCAST,
                                    "$outerClass"
                                )
                            )

                            // 新增局部变量,局部变量范围是整个方法
                            method.maxLocals++
                            val firstLabel: LabelNode = this.first {
                                it is LabelNode
                            } as LabelNode
                            val lastLabel: LabelNode = this.last {
                                it is LabelNode
                            } as LabelNode
                            method.localVariables.add(
                                LocalVariableNode(
                                    "weakActivity",
                                    "L$outerClass;",
                                    null,
                                    firstLabel,
                                    lastLabel,
                                    method.maxLocals
                                )
                            )
                            preNode = insertMethodNode(
                                this,
                                preNode,
                                VarInsnNode(ASTORE, method.maxLocals)
                            )
                            preNode = insertMethodNode(
                                this,
                                preNode,
                                VarInsnNode(ALOAD, method.maxLocals)
                            )

                            // 在代码最后加一个label和异常返回，但需要检查函数返回类型调用不同类型的xreturn
                            // 其实最简单粗暴的方法是直接抛出异常，但目的是为了解决内存泄漏问题，
                            // 对用户来说，异常闪退比内存泄漏体验更差，没必要为了解决内存泄漏而让用户体验降级
                            // 或许可以在非正式版本，如beta或者debug版本上直接抛异常
                            val returnNode =
                                this.find { it is InsnNode && it.opcode in IRETURN..RETURN }
                            val aheadReturnLabel = LabelNode(Label())
                            this.insert(this.last, aheadReturnLabel)
                            if (returnNode != null) {
                                when (returnNode.opcode) {
                                    IRETURN -> {
                                        this.insert(this.last, InsnNode(ICONST_0))
                                        this.insert(this.last, InsnNode(IRETURN))
                                    }

                                    LRETURN -> {
                                        this.insert(this.last, InsnNode(LCONST_0))
                                        this.insert(this.last, InsnNode(LRETURN))
                                    }

                                    FRETURN -> {
                                        this.insert(this.last, InsnNode(FCONST_0))
                                        this.insert(this.last, InsnNode(FRETURN))
                                    }

                                    DRETURN -> {
                                        this.insert(this.last, InsnNode(DCONST_0))
                                        this.insert(this.last, InsnNode(DRETURN))
                                    }

                                    ARETURN -> {
                                        this.insert(this.last, InsnNode(ACONST_NULL))
                                        this.insert(this.last, InsnNode(ARETURN))
                                    }

                                    RETURN -> {
                                        this.insert(this.last, InsnNode(RETURN))
                                    }
                                }
                            } else {
                                // 正常不会走到这个分支，每个方法都会有XXXreturn方法
                                println("this should not happen, every method should hava a return")
                                this.insert(this.last, InsnNode(RETURN))
                            }

                            // 增加判空逻辑
                            preNode = insertMethodNode(
                                this,
                                preNode,
                                JumpInsnNode(IFNULL, aheadReturnLabel)
                            )
                            // 判空之后需要再aload一次，保证后面调用正常
                            insertMethodNode(
                                this,
                                preNode,
                                VarInsnNode(ALOAD, method.maxLocals)
                            )
                        }
                    }
                }
            }
            super.visitEnd()
            accept(nextVisitor)  // 为啥要这里才accept？
        }

        fun insertMethodNode(
            list: InsnList,
            preNode: AbstractInsnNode,
            insertNode: AbstractInsnNode
        ): AbstractInsnNode {
            list.insert(preNode, insertNode)
            return insertNode
        }
    }
}