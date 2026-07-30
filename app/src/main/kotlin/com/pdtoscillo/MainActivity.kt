package com.pdtoscillo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.pdtoscillo.core.ui.theme.PdtTheme
import com.pdtoscillo.ui.PdtApp

/**
 * 単一 Activity。
 *
 * `configChanges` で画面回転を自前で扱い、通信セッションを作り直さない。
 * セッションは [PDToscilloApplication] が保持するため、Activity が再生成されても接続は維持される。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val session = (application as PDToscilloApplication).session

        setContent {
            PdtTheme {
                PdtApp(session = session)
            }
        }
    }
}
