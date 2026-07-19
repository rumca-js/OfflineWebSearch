# Project

 - offline web search (focus on search). It is an android app
 - allows user to search and find links from SQLite databases
 - this project description should be written as simple statements. Preferrably by lists
  
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

Contains state of database, name of local files, and remote path

# Views

 - EntryListScreen - provides search widget, shows list of entries (in each row)
 - EntryDetailScreen - detail screen of an entry: title, description, date of publish, etc.
 - EntryStatusScreen - screen showing if entry page returns correct HTTP status
 - EntryPreviewScreen - fetches page and shows dynamic entry preview: title, description, date of publish, etc.
 - OptionsScreen - contains configuration and setup of databases

## EntryListScreen

 - Provides search widget
 - Search suggestion are scrollable, with rows
 - scrolling list allows to load more entries

## EntryPreviewScreen

 - Uses generics, builder

## Search Operators

 - & - and
 - | - or
 - = - contains string
 - == - matches string

# Code

 - write small functions, divide into small files
 - provide doxygen for API, and classes
