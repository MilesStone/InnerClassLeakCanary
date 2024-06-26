
plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
}


repositories {
    maven { url=uri ("https://www.jitpack.io")}
    maven { url=uri ("https://maven.aliyun.com/repository/releases")}
    maven { url=uri ("https://maven.aliyun.com/repository/google")}
    maven { url=uri ("https://maven.aliyun.com/repository/central")}
    maven { url=uri ("https://maven.aliyun.com/repository/gradle-plugin")}
    maven { url=uri ("https://maven.aliyun.com/repository/public")}

    mavenCentral()
    google()
}

gradlePlugin {
    plugins {
        create("InnerClassOptimizePlugin") {
            id = "com.dfx.memleak.innerclass"
            implementationClass = "com.dfx.memleak.innerclass.InnerClassOptimizePlugin"
        }
    }
}
dependencies {
    implementation("com.android.tools.build:gradle-api:8.2.2")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")

}