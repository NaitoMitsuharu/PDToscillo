plugins {
    alias(libs.plugins.pdt.jvm.library)
    application
}

application {
    mainClass.set("com.pdtoscillo.simulator.MainKt")
}

// 疑似オシロスコープはアプリ側コードへ一切依存させない。
// 検証対象の解析ロジックを共有すると「自分で作った物を自分で解析して通る」テストになるため。
