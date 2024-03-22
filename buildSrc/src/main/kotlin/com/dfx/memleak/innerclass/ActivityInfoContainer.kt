package com.dfx.memleak.innerclass

import java.util.concurrent.CopyOnWriteArraySet

/**
 *
 * 这个类的目的是提前收集好所有Activity的类名，方便 @link InnerClassOptimizeCVFactory判断是否某个类是activity的内部类的
 * 但目前AGP8.0上的transform流程对于“先遍历收集，再修改字节码”这种方式的支持不太友好，导致这里只是做一个功能用途描述，实际无法这样使用
 * 或者可以单独弄一个gradle任务提前收集所有activity信息到文件仲，编译过程直接访问文件解析,
 * 或者提前解析AndroidManifest获取activity信息
 * see https://nebulae-pan.github.io/2021/12/25/Gradle7.0Transform%E6%9B%BF%E4%BB%A3%E6%96%B9%E6%A1%88/
 * todo: collect activity info for @link InnerClassOptimizeCVFactory
 */
object ActivityInfoContainer {
    private val set = CopyOnWriteArraySet<String>()

    fun addActivity(activity: String) {
        set.add(activity)
    }

    /**
     * todo:
     */
    fun isActivity(activity: String):Boolean {
        return activity=="com.example.myapplication.MainActivity"
//        set.contains(activity)
    }
    /**
     * todo:
     */
    fun isActivityInnerClass(className: String): Boolean {
        return className.contains("com.example.myapplication.MainActivity\$")
//        return set.contains(className.split("\$")[0])
    }



}