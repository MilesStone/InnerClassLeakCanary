package com.dfx.memleak.innerclass

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor


internal abstract class CollectActivityCVFactory: AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return nextClassVisitor
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        println(classData.superClasses.contains("android.app.Activity"))
        return false
    }
}