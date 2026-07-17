Open-Source, privacy-first bookmarking search engine. No cloud. No server. Just highly structured meta-database of valuable internet domains, communities, and personal web spaces directly on your local device.

It is designed for tech enthusiasts, privacy advocates, and anyone annoyed by search engine malvertising, SEO spam, and content farms.

# Features

 - Predefined database with common domains, YouTube channels
 - No cloud, no server. Fast local search with no waiting for server responses
 - Privacy-friendly: no network requests required for searching
 - Convenience links, services. Static auto RSS feeds discovery

# Links

[F-droid App](https://f-droid.org/en/packages/io.github.rumcajs.offlinewebsearch)

# Permissions

 - The app requires minimal permissions and does not rely on remote services for searching.
 - It does use 'network' access. The user might trigger a check of domain if is still available

# Databases

The application is highly customizable and allows you to load different databases directly from a file. You can curate your own search indexes, share them, or swap between specialized databases depending on your current needs—all completely offline.

## Ready Databases

 - [Feeds](https://github.com/rumca-js/awesome-database-feeds)
 - [Top Domains](https://github.com/rumca-js/awesome-database-top)
 - [Awesome Lists](https://github.com/rumca-js/awesome-database-awesomelists)
 - [Other](https://github.com/rumca-js/rumca-js.github.io/tree/main/data), [List of files](https://github.com/rumca-js/rumca-js.github.io/blob/main/data/databases.txt)

The databases need to comply specification maintained in the code of [linkarchivetools](https://github.com/rumca-js/linkarchivetools)

# Why use Offline Web Search?

 - some apps require you to have accounts, keep your data, sell your data
 - some apps require you to have a working self-hosted server (eg. karakeep)
 - some apps are bookmarking apps, maintain a lot of data. We want just a simple 'title', 'description' etc metadata, so highly optimized
 - some apps do not allow you to backup, share, or export your data
 - bookmark apps often focus on bookmarks. This app focus is on search
 - import is fast, since it uses SQLite, so it is easy to reuse in other projects ('linki' app was found to be slow since it performs HTML import export)
 - some apps might be better, but are not open source (eg. obsidian is proprietary)

# Screenshots

[Screenshots](https://github.com/rumca-js/OfflineWebSearch/tree/main/screenshot)

# Open Source

This project is open source and welcomes contributions, bug reports, and suggestions.
