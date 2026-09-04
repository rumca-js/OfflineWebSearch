package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.util.UrlServices

/**
 * Component that displays third-party archive and search service links (UrlServices) for an entry link.
 */
@Composable
fun UrlServicesPane(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier
) {
    val link = entry.link ?: return
    val urlServices = UrlServices()
    val serviceLinks = urlServices.getServiceLinks(link)
    if (serviceLinks.isEmpty()) return

    PropertiesPane(
        modifier = modifier,
        properties = serviceLinks.map { (serviceName, serviceUrl) ->
            PropertyItem(
                label = serviceName,
                value = serviceUrl,
                type = PropertyType.LINK,
                isRestricted = isRestricted,
                toastMessage = "$serviceName link copied"
            )
        }
    )
}

