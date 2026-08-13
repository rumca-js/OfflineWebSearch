# Project

 - offline web search (focus on search). It is an android app
 - allows user to search and find links from SQLite databases (and in JSON files)
 - this project description should be written as simple statements. Preferably by lists
  
 - user is able to provide databases:
   -- .db file is a file that this application can use
   -- .json file is a file that this application can use
   -- .zip is archive of JSONs
   -- .db.zip is an archive of SQLite file

# Databases

.db.zip files shall be downloaded from link, unpacked to app memory, and used from there as .db SQLite files
.db files shall be downloaded from internet link, stored in app memory, and used from there as .db SQLite file
local SQLite, or JSON files can be used as is

## DatabaseState

 - contains state of database, name of local files, and remote path

# Views

 - EntryListScreen - provides search widget, shows list of entries (in each row).
 - EntryDetailScreen - detail screen of an entry: title, description, date of publish, etc.
 - EntryStatusScreen - screen showing if entry page returns correct HTTP status
 - EntryPreviewScreen - fetches page and shows dynamic entry preview: title, description, date of publish, etc.
 - OptionsScreen - contains configuration and setup of databases
 - SourceScreen - contains source screen
 - SourcesScreen - contains list of sources
 - AboutScreen - screen with information about project

## EntryListScreen

 - Provides search widget
 - Search suggestion are scrollable, with rows
 - scrolling list allows to load more entries
 - page navigation elements (prev, next button) are in scrollable area, after search results
 - after page navigation add new result button should be present
 - clicking on entry opens EntryDetailScreen

## EntryDetailScreen
Provides bar on top with buttons: edit, share, preview, remove

## EntryStatusScreen
Fetches page status from the internet. Provides button 'update entry data'. The button updates status code for the entry

## EntryPreviewScreen
Fetches page dynamically from web. Provides button 'update entry data'. The button updates title, description, or other properties are updated for the entry

## OptionsScreen

 - Database can be added from preconfigured list available at https://rumca-js.github.io/data/databases.txt
 - Database can be added from local filesystem
 - Database can be added from url
 - Database can be created empty (from assets SQLite table)
 - Database fetched from the internet can be re-fetched. User is notified that it destroys current local database
 - Database can be shared (to other apps), saved as a file
 - List of databases show state of database (DOWNLOADING, READY, FAILED)
 - each database in list contain button to refresh, remove
 - clicking on a database opens DatabaseScreen

## DatabaseScreen
Provides bar on top with buttons: edit, share, refresh, remove

Shows status of database, and count of table rows.

Provides button to clear search, user visited elements.

## SourcesScreen
Provides bar on top with buttons: edit, fetch, remove.

Selecting source opens SourceScreen.

Source fetch means that body of page is fetched, should be RSS, entries are read from it, and inserted into linkdatamodel table.

# Operations 
## Search widget operators

SQLite search capabilities should be handled. For example:
 - title LIKE %something%
 - description LIKE %something%

## Browsing
Every entry visit is maintained in table uservists.

# Data

app/src/main/java/io/github/rumcajs/offlinewebsearch/data maintains data files. The file wrap SQLite tables and provide assessors to model. For example SourceRepository is wrapper for sourcedatamodel table.

# Code

 - write small functions, divide into small files
 - provide doxygen for API, and classes
