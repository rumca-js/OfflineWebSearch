package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a [SourceIcon.value] string (Material icon name) to the corresponding [ImageVector].
 *
 * Returns null when the name is not recognised, allowing callers to render a fallback.
 */
fun sourceIconImageVector(name: String): ImageVector? = when (name) {
    "email"            -> Icons.Default.Email
    "chat"             -> Icons.Default.Chat
    "forum"            -> Icons.Default.Forum
    "sms"              -> Icons.Default.Sms
    "rss_feed"         -> Icons.Default.RssFeed
    "article"          -> Icons.Default.Article
    "newspaper"        -> Icons.Default.Newspaper
    "book"             -> Icons.Default.Book
    "menu_book"        -> Icons.Default.MenuBook
    "notes"            -> Icons.Default.Notes
    "people"           -> Icons.Default.People
    "person"           -> Icons.Default.Person
    "group"            -> Icons.Default.Group
    "public"           -> Icons.Default.Public
    "share"            -> Icons.Default.Share
    "podcasts"         -> Icons.Default.Podcasts
    "videocam"         -> Icons.Default.Videocam
    "movie"            -> Icons.Default.Movie
    "music_note"       -> Icons.Default.MusicNote
    "image"            -> Icons.Default.Image
    "photo_camera"     -> Icons.Default.PhotoCamera
    "code"             -> Icons.Default.Code
    "terminal"         -> Icons.Default.Terminal
    "bug_report"       -> Icons.Default.BugReport
    "computer"         -> Icons.Default.Computer
    "smartphone"       -> Icons.Default.Smartphone
    "cloud"            -> Icons.Default.Cloud
    "science"          -> Icons.Default.Science
    "biotech"          -> Icons.Default.Biotech
    "school"           -> Icons.Default.School
    "local_library"    -> Icons.Default.LocalLibrary
    "manage_search"    -> Icons.Default.ManageSearch
    "business"         -> Icons.Default.Business
    "account_balance"  -> Icons.Default.AccountBalance
    "trending_up"      -> Icons.Default.TrendingUp
    "payments"         -> Icons.Default.Payments
    "gavel"            -> Icons.Default.Gavel
    "balance"          -> Icons.Default.Balance
    "policy"           -> Icons.Default.Policy
    "sports"           -> Icons.Default.Sports
    "fitness_center"   -> Icons.Default.FitnessCenter
    "health_and_safety"-> Icons.Default.HealthAndSafety
    "local_hospital"   -> Icons.Default.LocalHospital
    "nature"           -> Icons.Default.Nature
    "wb_sunny"         -> Icons.Default.WbSunny
    "language"         -> Icons.Default.Language
    "star"             -> Icons.Default.Star
    "bookmark"         -> Icons.Default.Bookmark
    "link"             -> Icons.Default.Link
    "info"             -> Icons.Default.Info
    "search"           -> Icons.Default.Search
    else               -> null
}

/**
 * Renders a Material icon identified by its [name] string (i.e. a [SourceIcon.value]).
 *
 * Falls back to [Icons.Default.Image] when the name is not found in the known mapping.
 *
 * @param name             The icon identifier — must match a value in [SourceIcons.all].
 * @param contentDescription Accessibility description passed to [Icon].
 * @param modifier         Optional layout modifier.
 * @param tint             Icon tint colour; defaults to [MaterialTheme.colorScheme.onSurface].
 */
@Composable
fun MaterialSymbolIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val vector = sourceIconImageVector(name) ?: Icons.Default.Image
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
