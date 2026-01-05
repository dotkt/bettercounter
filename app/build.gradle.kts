buildscript {
    dependencies {
        classpath(libs.android.gradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
}

// 获取git commit hash前6位（兼容配置缓存：使用文件缓存）
// 注意：配置阶段只读取文件，不执行外部进程
// 第一次构建时文件可能不存在，会使用 "unknown"，构建后文件会被生成
val gitHashFile = layout.projectDirectory.file(".git_hash").asFile
val gitCommitHash = if (gitHashFile.exists()) {
    gitHashFile.readText().trim().takeIf { it.isNotEmpty() } ?: "unknown"
} else {
    "unknown"
}

// 创建任务来生成 git hash 文件（在任务执行阶段，不影响配置缓存）
tasks.register("generateGitHash") {
    val outputFileProvider = layout.projectDirectory.file(".git_hash")
    val outputFile = outputFileProvider.asFile
    outputs.file(outputFile)
    
    doLast {
        // 在任务执行时获取项目目录（兼容配置缓存）
        val currentDir = outputFile.parentFile
        var rootDir: File? = currentDir.parentFile
        while (rootDir != null && !File(rootDir, ".git").exists()) {
            rootDir = rootDir.parentFile
        }
        val rootProjectDir = rootDir ?: currentDir.parentFile ?: currentDir
        
        try {
            val process = ProcessBuilder("git", "rev-parse", "--short=6", "HEAD")
                .directory(rootProjectDir)
                .redirectErrorStream(true)
                .start()
            
            val hash = process.inputStream.bufferedReader().use { it.readLine()?.trim() ?: "unknown" }
            val exitValue = process.waitFor()
            
            if (exitValue == 0 && hash.isNotEmpty() && hash != "unknown") {
                outputFile.writeText(hash)
                println("Generated git hash: $hash")
            } else {
                outputFile.writeText("unknown")
                println("Failed to get git hash, using 'unknown'")
            }
        } catch (e: Exception) {
            outputFile.writeText("unknown")
            println("Error getting git hash: ${e.message}, using 'unknown'")
        }
    }
}

// 让所有编译任务依赖于 generateGitHash
tasks.named("preBuild").configure {
    dependsOn("generateGitHash")
}

android {
    namespace = "org.kde.bettercounter"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.kde.bettercounter"
        minSdk = 21
        targetSdk = 34
        versionCode = 40900
        versionName = "4.9.0"
        
        // 添加git commit hash到BuildConfig（从文件读取，兼容配置缓存）
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        generateLocaleConfig = true
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = " Dev"
        }
    }
}

dependencies {
    implementation(libs.douglasjunior.android.simple.tooltip)
    implementation(libs.philJay.mpAndroidChart)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
    coreLibraryDesugaring(libs.android.desugarJdkLibs) // Chrono.UNITS for Android < 26
    testImplementation(libs.junit)
}
