package org.example

import com.google.auto.service.AutoService

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter


private const val ENABLED = "enabled"
private const val ANNOTATIONS = "annotations"
private val KEY_ENABLED = CompilerConfigurationKey<Boolean>(ENABLED)
private val KEY_ANNOTATIONS = CompilerConfigurationKey<List<String>>(ANNOTATIONS)
private const val PLUGIN_ID = "kotlin-log"

@AutoService(CommandLineProcessor::class)
class LogCommandLineProcessor : CommandLineProcessor {

    // 配置 Kotlin 插件唯一 ID
    override val pluginId: String
        get() = PLUGIN_ID

    // 读取 `SubPluginOptions` 参数，并写入 `CliOption`
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(ENABLED, "<true|false>", "Whether to enable the plugin"),
        CliOption(ANNOTATIONS, "<annotation class>", "Annotation class to be processed"),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            ENABLED -> configuration.put(KEY_ENABLED, value.toBoolean())
            ANNOTATIONS -> configuration.appendList(KEY_ANNOTATIONS, value)
            else -> error("Unexpected config option ${option.optionName}")
        }
    }

}

@AutoService(ComponentRegistrar::class)
class LogComponentRegistrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {

        if (configuration[KEY_ENABLED] == false || configuration[KEY_ANNOTATIONS].isNullOrEmpty()) {
            return
        }

        // 获取日志收集器
        val messageCollector =
            configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        // 输出日志，查看是否执行
        // CompilerMessageSeverity.INFO - 没有看到日志输出
        // CompilerMessageSeverity.ERROR - 编译过程停止执行
        messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "Welcome to kotlin compiler plugin"
        )

        // 此处在 `ClassBuilderInterceptorExtension` 中注册扩展
        ClassBuilderInterceptorExtension.registerExtension(
            project,
            LogClassGenerationInterceptor(messageCollector, configuration[KEY_ANNOTATIONS]!!)
        )

    }

}

class LogClassGenerationInterceptor(
    private val messageCollector: MessageCollector,
    private val debugLogAnnotations: List<String>
) : ClassBuilderInterceptorExtension {

    // 拦截 ClassBuilderFactory
    override fun interceptClassBuilderFactory(
        interceptedFactory: ClassBuilderFactory,
        bindingContext: BindingContext,
        diagnostics: DiagnosticSink
    ): ClassBuilderFactory = object : ClassBuilderFactory by interceptedFactory {

        // 复写 newClassBuilder，自定义 ClassBuilder
        override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {

            // 传入源ClassBuilder
            return LogClassBuilder(messageCollector, debugLogAnnotations, interceptedFactory.newClassBuilder(origin))
        }

    }

}

class LogClassBuilder(
    private val messageCollector: MessageCollector,
    private val debugLogAnnotations: List<String>,
    private val delegateBuilder: ClassBuilder
) : DelegatingClassBuilder() {

    override fun getDelegate(): ClassBuilder {
        return delegateBuilder
    }

    override fun newMethod(
        origin: JvmDeclarationOrigin,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {

        val original = super.newMethod(origin, access, name, desc, signature, exceptions)

        // 判断是否是方法/函数描述符
        val function = origin.descriptor as? FunctionDescriptor
            ?: return original

        if (debugLogAnnotations.none { function.annotations.hasAnnotation(FqName(it)) }) {
            return original
        }

        messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "function ${function.name} has annotation ${function.annotations}}"
        )

        return object : MethodVisitor(Opcodes.ASM5, original) {

            private var newMaxLocals = 0
            private var startTimeIndex = 10
            private var endTimeIndex = 15

            override fun visitMaxs(maxStack: Int, maxLocals: Int) {

                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "visitMaxs maxStack: $maxStack, maxLocals: $maxLocals"
                )

                super.visitMaxs(maxStack, maxLocals)
            }

            override fun visitCode() {

                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "visitCode -> ${function.name}"
                )

                InstructionAdapter(this).apply {
                    // 生成 System.out.println("-> test2 start")
                    getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                    visitLdcInsn("-> ${function.name} start")
                    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)

                    // 生成 val startTime = System.currentTimeMillis()
                    invokestatic(Type.getType(System::class.java).internalName, "currentTimeMillis", "()J", false)
                    store(startTimeIndex, Type.LONG_TYPE)
                }

                super.visitCode()
            }

            override fun visitInsn(opcode: Int) {

                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "visitCode -> ${function.name} opcode: $opcode"
                )

                when (opcode) {
                    Opcodes.RETURN, Opcodes.ARETURN, Opcodes.IRETURN -> {
                        InstructionAdapter(this).apply {

                            // 获取结束时间，计算时间差，并存储到局部变量3
                            invokestatic(
                                Type.getType(System::class.java).internalName,
                                "currentTimeMillis",
                                "()J",
                                false
                            )
                            load(startTimeIndex, Type.LONG_TYPE)
                            sub(Type.LONG_TYPE)
                            store(endTimeIndex, Type.LONG_TYPE)

                            //打印 "<- test2 end cost " 和时间差，以及 " ms"
                            getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                            anew(Type.getType(StringBuilder::class.java))
                            dup()
                            invokespecial("java/lang/StringBuilder", "<init>", "()V", false)
                            visitLdcInsn("<- ${function.name} end cost ")
                            invokevirtual(
                                "java/lang/StringBuilder",
                                "append",
                                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                                false
                            )
                            load(endTimeIndex, Type.LONG_TYPE)
                            invokevirtual(
                                "java/lang/StringBuilder",
                                "append",
                                "(J)Ljava/lang/StringBuilder;",
                                false
                            )
                            visitLdcInsn(" ms")
                            invokevirtual(
                                "java/lang/StringBuilder",
                                "append",
                                "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                                false
                            )
                            invokevirtual(
                                "java/lang/StringBuilder",
                                "toString",
                                "()Ljava/lang/String;",
                                false
                            )
                            invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
                        }
                    }
                }
                super.visitInsn(opcode)
            }
        }
    }

}

