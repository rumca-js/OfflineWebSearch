package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rumcajs.offlinewebsearch.data.SocialData
import io.github.rumcajs.offlinewebsearch.ui.screens.DetailRow

/**
 * Returns true if all statistics fields are null, 0, or blank/empty string.
 * dateUpdated should not be included in check.
 */
fun SocialData.isEmptyOrZero(): Boolean {
    val isZeroOrNull = { v: Int? -> v == null || v == 0 }
    return isZeroOrNull(thumbsUp) &&
            isZeroOrNull(thumbsDown) &&
            isZeroOrNull(viewCount) &&
            isZeroOrNull(rating) &&
            isZeroOrNull(upvoteRatio) &&
            isZeroOrNull(upvoteDiff) &&
            isZeroOrNull(upvoteViewRatio) &&
            isZeroOrNull(stars)
}

/**
 * Component that displays social data (thumbs, view count, stars rating, rating, upvote ratio, etc.) for an entry.
 */
@Composable
fun SocialDataPane(
    socialData: SocialData?,
    modifier: Modifier = Modifier
) {
    if (socialData == null || socialData.isEmptyOrZero()) return

    Column(modifier = modifier.fillMaxWidth()) {
        val hasThumbsUp = socialData.thumbsUp?.takeIf { it != 0 } != null
        val hasThumbsDown = socialData.thumbsDown?.takeIf { it != 0 } != null
        val hasViewCount = socialData.viewCount?.takeIf { it != 0 } != null
        val hasStars = socialData.stars?.takeIf { it != 0 } != null

        if (hasThumbsUp || hasThumbsDown || hasViewCount || hasStars) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasThumbsUp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Thumbs Up",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = socialData.thumbsUp.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (hasThumbsDown) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = "Thumbs Down",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = socialData.thumbsDown.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (hasViewCount) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Views",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = socialData.viewCount.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (hasStars) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Stars",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = socialData.stars.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        socialData.stars?.takeIf { it != 0 }?.let { starValue ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stars",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )

                // If stars is a 1-5 rating (or similar small number), render star icons visually
                if (starValue in 1..5) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in 1..5) {
                            val icon = if (i <= starValue) Icons.Default.Star else Icons.Default.StarOutline
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(text = starValue.toString())
                    }
                }
            }
        }

        /*
        socialData.rating?.takeIf { it != 0 }?.let {
            DetailRow(label = "Rating", value = it.toString())
        }
        socialData.upvoteRatio?.takeIf { it != 0 }?.let {
            DetailRow(label = "Upvote Ratio", value = "$it%")
        }
        socialData.upvoteDiff?.takeIf { it != 0 }?.let {
            DetailRow(label = "Upvote Diff", value = it.toString())
        }
        socialData.upvoteViewRatio?.takeIf { it != 0 }?.let {
            DetailRow(label = "Upvote/View Ratio", value = "$it%")
        }
        socialData.dateUpdated?.takeIf { it.isNotBlank() }?.let {
            DetailRow(label = "Updated", value = it)
        }
        */
    }
}

