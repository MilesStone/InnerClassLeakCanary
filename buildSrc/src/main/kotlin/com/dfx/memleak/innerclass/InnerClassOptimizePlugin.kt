package com.dfx.memleak.innerclass

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project

open class InnerClassOptimizePlugin: Plugin<Project> {
    override fun apply(target: Project) {
        val androidComponents= target.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant: Variant ->
            variant.instrumentation.transformClassesWith(InnerClassOptimizeCVFactory::class.java, InstrumentationScope.ALL) {}
            // 增加字节码后可能会影响max_frame, 增加下面参数可以重新计算
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
        }
    }
}
