package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.DatabaseStatus

@Composable
fun StatusBadge(
    status: DatabaseStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = when (status) {
        DatabaseStatus.READY -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "READY")
        DatabaseStatus.FAILED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "FAILED")
        DatabaseStatus.DOWNLOADING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "DOWNLOADING")
        DatabaseStatus.UNPACKING -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), "UNPACKING")
        DatabaseStatus.INIT -> Triple(Color(0xFFF5F5F5), Color(0xFF616161), "INIT")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ReadOnlyBadge(
    isReadOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = if (isReadOnly) {
        Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), "READ-ONLY")
    } else {
        Triple(Color(0xFFE0F2F1), Color(0xFF00796B), "READ-WRITE")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
