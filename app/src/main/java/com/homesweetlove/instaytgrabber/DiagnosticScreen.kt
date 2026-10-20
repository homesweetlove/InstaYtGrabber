package com.homesweetlove.instaytgrabber

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import com.homesweetlove.instaytgrabber.ui.Toss
import com.homesweetlove.instaytgrabber.ui.TossType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val errors = DiagnosticLog.entries.asReversed()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
            color = Toss.Bg,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("오류 기록", style = TossType.Section, color = Toss.Text1)
                        Text("${errors.size}건 · 최근 7일 · 최대 120건", style = TossType.Caption, color = Toss.Text3)
                    }
                    TextButton(onClick = onClose) { Text("닫기") }
                }
                Text(
                    "프로필 불러오기를 다시 시도한 뒤 실패한 단계를 확인하세요. " +
                        "URL과 세션 값은 저장 전 가려집니다. 기록은 기기에만 저장됩니다.",
                    style = TossType.Caption,
                    color = Toss.Text3,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        manager.setPrimaryClip(
                            ClipData.newPlainText("InstaYtGrabber 오류 기록", DiagnosticLog.report())
                        )
                        Toast.makeText(context, "오류 기록을 복사했어요", Toast.LENGTH_SHORT).show()
                    }) { Text("전체 복사") }
                    TextButton(onClick = {
                        DiagnosticLog.clear()
                        Toast.makeText(context, "오류 기록을 삭제했어요", Toast.LENGTH_SHORT).show()
                    }, enabled = errors.isNotEmpty()) { Text("기록 삭제") }
                }
                if (errors.isEmpty()) {
                    Text(
                        "아직 기록된 오류가 없습니다. Instagram 프로필을 불러와 보세요.",
                        style = TossType.BodyRegular,
                        color = Toss.Text3,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(errors) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Toss.White, RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    item.code + " · " + item.stage,
                                    style = TossType.Body.copy(fontWeight = FontWeight.SemiBold),
                                    color = Toss.Text1
                                )
                                Text(
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        .format(Date(item.timeMillis)),
                                    style = TossType.Caption,
                                    color = Toss.Text3
                                )
                                Text(
                                    item.message,
                                    style = TossType.BodyRegular,
                                    color = Toss.Text2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
