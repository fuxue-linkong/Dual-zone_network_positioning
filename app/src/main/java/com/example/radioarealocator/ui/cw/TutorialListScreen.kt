package com.example.radioarealocator.ui.cw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.ui.theme.LocalCardAlpha

data class TutorialLesson(
    val id: Int,
    val title: String,
    val description: String,
    val progress: Float, // 0.0 to 1.0
    val totalLessons: Int = 1,
    val completedLessons: Int = 0
)

@Composable
fun TutorialListScreen(
    onLessonClick: (Int) -> Unit,
    courseProgress: Map<Int, Float> = emptyMap(),
    contentPadding: PaddingValues
) {
    val lessons = listOf(
        TutorialLesson(
            id = 1,
            title = "Koch课程",
            description = "使用Koch方法渐进式学习摩尔斯电码",
            progress = courseProgress[1] ?: 0.0f,
            totalLessons = 26,
            completedLessons = ((courseProgress[1] ?: 0.0f) * 26).toInt()
        ),
        TutorialLesson(
            id = 2,
            title = "字符组练习",
            description = "练习特定字符组合，提高识别速度",
            progress = courseProgress[2] ?: 0.0f,
            totalLessons = 10,
            completedLessons = ((courseProgress[2] ?: 0.0f) * 10).toInt()
        ),
        TutorialLesson(
            id = 3,
            title = "呼号训练",
            description = "练习业余无线电呼号抄收",
            progress = courseProgress[3] ?: 0.0f,
            totalLessons = 10,
            completedLessons = ((courseProgress[3] ?: 0.0f) * 10).toInt()
        ),
        TutorialLesson(
            id = 4,
            title = "文本训练",
            description = "练习完整CW通联文本",
            progress = courseProgress[4] ?: 0.0f,
            totalLessons = 10,
            completedLessons = ((courseProgress[4] ?: 0.0f) * 10).toInt()
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(lessons) { lesson ->
            TutorialLessonItem(
                lesson = lesson,
                onClick = { onLessonClick(lesson.id) }
            )
        }
    }
}

@Composable
private fun TutorialLessonItem(
    lesson: TutorialLesson,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = lesson.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "进度: ${lesson.completedLessons}/${lesson.totalLessons} (${(lesson.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { lesson.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
