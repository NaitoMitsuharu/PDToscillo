package com.pdtoscillo.simulator

/**
 * 疑似オシロスコープの起動口。
 *
 * ```bash
 * ./gradlew :simulator:run --args="--port 4000 --model MDO4104C"
 * ./gradlew :simulator:run --args="--model DPO4054 --fault SPLIT_RESPONSE --verbose"
 * ```
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    if (options == null) {
        printUsage()
        return
    }

    val simulator = ScopeSimulator(options)
    val port = simulator.start()
    println("疑似オシロスコープを起動しました。")
    println("  ポート      : $port")
    println("  モデル      : ${options.model.idnModel} (${options.model.idnResponse})")
    println("  障害モード  : ${options.faultMode}")
    println("  波形        : ${options.waveformShape}")
    println("  Terminal    : ${options.terminalMode}")
    println("停止するには Ctrl+C を押してください。")

    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("停止します。")
            simulator.close()
        },
    )

    // アクセプトループは別スレッドで動くため、ここでは Ctrl+C まで待機するだけ。
    java.util.concurrent.CountDownLatch(1).await()
}

private fun parseArgs(args: Array<String>): SimulatorConfig? {
    var config = SimulatorConfig()
    var index = 0
    while (index < args.size) {
        when (val key = args[index]) {
            "--port" -> {
                val value = args.getOrNull(++index)?.toIntOrNull() ?: return null
                config = config.copy(port = value)
            }

            "--bind" -> {
                val value = args.getOrNull(++index) ?: return null
                config = config.copy(bindAddress = value)
            }

            "--model" -> {
                val name = args.getOrNull(++index) ?: return null
                val model = SimulatedModel.fromName(name) ?: run {
                    println("不明なモデル: $name")
                    println("使用できるモデル: ${SimulatedModel.entries.joinToString { it.idnModel }}")
                    return null
                }
                config = config.copy(model = model)
            }

            "--fault" -> {
                val name = args.getOrNull(++index) ?: return null
                val mode = FaultMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: run {
                    println("不明な障害モード: $name")
                    println("使用できる値: ${FaultMode.entries.joinToString { it.name }}")
                    return null
                }
                config = config.copy(faultMode = mode)
            }

            "--shape" -> {
                val name = args.getOrNull(++index) ?: return null
                val shape = WaveformShape.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
                config = config.copy(waveformShape = shape)
            }

            "--terminal" -> config = config.copy(terminalMode = true)
            "--verbose" -> config = config.copy(verbose = true)
            "--help", "-h" -> return null
            else -> {
                println("不明な引数: $key")
                return null
            }
        }
        index++
    }
    return config
}

private fun printUsage() {
    println(
        """
        使い方: simulator [オプション]

          --port <番号>     待受ポート (既定 ${SimulatorConfig.DEFAULT_PORT}、0 で自動割当)
          --bind <アドレス> 待受アドレス (既定 ${SimulatorConfig.DEFAULT_BIND_ADDRESS}、LAN 越しは 0.0.0.0)
          --model <名前>    ${SimulatedModel.entries.joinToString { it.idnModel }}
          --fault <モード>  ${FaultMode.entries.joinToString { it.name }}
          --shape <形状>    ${WaveformShape.entries.joinToString { it.name }}
          --terminal        Protocol: Terminal を模してエコーとプロンプトを返す
          --verbose         受信コマンドを表示する
          --help            この説明を表示する
        """.trimIndent(),
    )
}
