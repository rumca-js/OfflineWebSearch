# Project

 - The project is an Android application.
 - It shall not contain dependencies toward google, nor its services (if possible)
 - The application provides offline web search.
 - The primary function of the application is searching.
 - The application searches local SQLite databases and JSON files.
 - Search results contain links to web pages.
 - The application does not require an internet connection to search local databases.
 - Internet access is required when fetching databases, sources, or web pages.
 - This instruction should contain brief statements.

A database is a searchable collection of entries.
A database can be backed by SQLite or JSON files.

 # Supported Database Files
 The application supports the following file types:
 - .db - SQLite database file.
 - .json - JSON file containing entries.
 - .zip - Archive containing JSON files.
 - .db.zip - Archive containing an SQLite database file.

Users can provide databases from:
 - Local filesystem.
 - Remote URL.
 - Preconfigured database list.

# SQLite data model

 - The SQLite data model is defined by linkarchivetools.
 - The authoritative model definition is: https://github.com/rumca-js/linkarchivetools/blob/main/linkarchivetools/model/definitions.py
 - The Android application must remain compatible with the defined SQLite schema.
 - Entries - linkdatamodel
 - Sources - sourcedatamodel
 - Search Views - searchview
 - Configuration - configurationentry
 - Visited Entries History - entryvisithistory
 - Entry Transitions History - entrytransitionhistory
 - Read Later - readlater

# Database handling
 - any internet database is downloaded to application storage, and used from there
 - any archived database is unpacked, and used from unpacked file
 - local files can be used without copying them when Android permissions allow direct access.
 - Downloaded databases can be downloaded again.
 - Re-fetching a database replaces the current local copy.
 - The user is notified before a local database is replaced.
 - The application maintains the state of every configured database.
 - Only one database is active at a time.

# DatabaseState

DatabaseState contains the state of a configured database.

It contains:
 - Database name.
 - Local file path.
 - Remote URL, if applicable.
 - Database type.
 - Current state.
 - Download or update information, if applicable.
 - Error information, if the state is FAILED.

Possible database states include:
 - DOWNLOADING
 - UNPACKING
 - READY
 - FAILED

# Views

 - EntryListScreen - Provides the search interface. Shows search results.
 - VisitedEntriesScreen - Shows entries visited by the user.
 - ReadLaterScreen - Shows entries saved to read later.
 - EntryDetailScreen - Shows details of a selected entry.
 - EntryEditScreen - Adds or edits an entry.
 - EntryStatusScreen - Checks the HTTP status of an entry URL.
 - EntryPreviewScreen - Fetches and displays the current web page data.
 - OptionsScreen - Configures databases and application settings.
 - DatabasesScreen - Shows the list of configured databases.
 - DatabaseScreen - Shows information and actions for a database.
 - SourceScreen - Shows information and actions for a source.
 - SourceEditScreen - Adds or edits a source.
 - SourcesScreen - Shows the list of configured sources.
 - AboutScreen - Shows information about the application.

## EntryListScreen

 - Provides search widget
 - Provides a visited filter chip to toggle filtering results to visited entries (visible when `trackUserNavigation` is enabled).
 - Provides a read later filter chip to toggle filtering results to saved read later entries (visible when the active database is writable).
 - Search supports advanced search capabilities (like SQLite syntax).
 - Search suggestion are scrollable, with rows
 - The list supports loading additional results.
 - Search results support page navigation (or dynamic loading of next / prev elements)
 - page navigation elements (prev, next button) are in scrollable area, after search results
 - after page navigation add new result button should be present
 - Selecting an entry opens EntryDetailScreen.
 - Implement pull to refresh

## VisitedEntriesScreen
 - Displays the list of entries visited by the user stored in `entryvisithistory`.
 - Selecting an entry opens EntryDetailScreen.
 - Provides an action to clear all visited entry history.
 - Supports pull to refresh.

## ReadLaterScreen
 - Displays the list of entries saved to read later stored in `readlater`.
 - Selecting an entry opens EntryDetailScreen.
 - Provides an action to clear all read later entries.
 - Supports pull to refresh.

## Search
 - Search is performed against the selected database.
 - Search expressions are translated to the underlying database format.
    -- SQLite databases use SQLite search capabilities.
    -- JSON databases provide equivalent search behavior where possible.
 - Search can match the entry title.
 - Search can match the entry description.
 - Search can support expressions such as:
 - title LIKE '%something%'
 - description LIKE '%something%'
 - Search implementation should use appropriate SQLite indexes where possible.
 - The search implementation should support large databases efficiently.
 - provide ability to support order (at least) by
    -- page rating votes
    -- date created
    -- date published
    -- followers count
    -- stars
    -- page rating visits

## EntryDetailScreen
Displays the details of an entry.

Provides a top bar with the following actions:
 - Check later (adds or removes entry from Read Later).
 - Edit.
 - Share.
 - Preview.
 - Remove.

The screen displays, where available:
 - Thumbnail, or video playback frame
 - Title.
 - Link.
 - Description.
 - Publication date.
 - Other entry metadata.
 - Entry transition pane. The pane shows entries that the user previously visited from the current entry. Entry transitions are stored in UserEntryTransitionHistory.

## EntryEditScreen
 - Allows adding a new entry or editing an existing entry.
 - Provides input fields for entry metadata (URL, title, description, tags, author, etc.).
 - Saves changes to the active database.
 - Only accessible when the active database is writable.

## EntryStatusScreen
 - Fetches the entry URL from the internet.
 - Displays the HTTP response status.
 - Displays whether the page is reachable.
 - Provides an Update entry data button.
 - The button updates the stored HTTP status information for the entry.

## EntryPreviewScreen
 - Fetches the entry page from the internet.
 - Displays a dynamic preview of the page.
 - The preview can display:
    -- Title.
    -- Description.
    -- Publication date.
    -- Other available page metadata.
 - Provides an Update entry data button.
 - The button updates the stored entry metadata.

## OptionsScreen
 - Displays the configured databases.
 - Provides navigation to DatabasesScreen and AboutScreen.
 - Configures search settings and application preferences.

## DatabasesScreen
 - Displays the list of configured databases.
 - Database can be added from preconfigured list available at https://rumca-js.github.io/data/databases.txt
 - Database can be added from local filesystem
 - Database can be added from url
 - Database can be created empty (from assets SQLite table)
 - Database fetched from the internet can be re-fetched. User is notified that it destroys current local database
 - Database can be shared (to other apps), saved as a file
 - List of databases show state of database (DOWNLOADING, UNPACKING, READY, FAILED)
 - Each database in list contains buttons to Refresh, Remove
 - Selecting a database opens DatabaseScreen.

## DatabaseScreen
 - Displays information about the selected database.
 - Provides a top bar with the following actions:
    -- Edit.
    -- Share.
    -- Refresh.
    -- Remove.
 - Displays the current database state.
 - Displays the database name.
 - Displays the database type.
 - Displays the local file information.
 - Displays the remote URL, if applicable.
 - Displays the number of rows in relevant tables.
 - Provides an action to clear the search history.
 - Provides an action to clear the user's visited entries.
 - Provides an action to clear the user's read later entries.

## SourcesScreen
 - Provides bar on top with buttons: add, edit, fetch, remove.
 - Selecting source opens SourceScreen.
 - Source fetch means that body of page is fetched, should be RSS, entries are read from it, and inserted into linkdatamodel table.

## SourceScreen
 - Displays information about a source.
 - Provides a top bar with the following actions:
    -- Edit.
    -- Fetch.
    -- Remove.
 - The Fetch action downloads the source content from the internet.
 - The source content is expected to be RSS or a compatible feed.
 - Entries are extracted from the source content.
 - Extracted entries are inserted into the linkdatamodel table.
 - Existing entries should not be duplicated.

## SourceEditScreen
 - Allows adding a new RSS source or editing an existing source.
 - Provides input fields for source title, URL, and enabled status.
 - Saves changes to the sourcedatamodel table in the active database.
 - Only accessible when the active database is writable.

# Browsing
 - Every entry visit is recorded (if configured so).
 - Visit information is stored in the UserEntryVisitHistory SQLite table (`entryvisithistory`).
 - The application can display the user's visited entries.
 - The application can clear the user's visit history.
 - Entries can be saved to read later, stored in the `readlater` SQLite table.
 - The application can display and clear the user's read later entries.
 - When EntryDetailScreen is visited, it contains pane below to which entries user transitions to.
 - Entry transitions are maintained by UserEntryTransitionHistory SQLite table (`entrytransitionhistory`).

# Data Layer
 - app/src/main/java/io/github/rumcajs/offlinewebsearch/data contains the data layer.
 - The data layer provides access to application data.
 - The data layer wraps SQLite tables and provides accessors for the model.
 - Each repository is responsible for a specific data model or table.
 - For example, SourceRepository provides access to the sourcedatamodel table.
 - Database access should not be performed directly from UI screens.
 - UI screens should access data through the appropriate repository or data-layer interface.

# Code Requirements
 - Use small functions.
 - Keep classes small and focused.
 - Divide functionality into small files.
 - Give each class a clear responsibility.
 - Avoid duplicating database access logic.
 - Keep UI code separate from database and networking code.
 - Use repositories for database access.
 - Use appropriate abstractions for downloading files and fetching web pages.
 - Provide API documentation using Doxygen-style comments.
 - Document public classes.
 - Document public methods.
 - Document non-obvious implementation decisions.
 - Use descriptive names for classes, methods, and variables.
 - Prefer simple implementations over unnecessary abstractions.
 - When handling SQLite tables, then apply some storage limits. For example, we may store history of user searches. Do not allow for infinite growth, but allow only a fixed amount of entries to be added. New entries replace old ones. One exception is Entries. There is no limit for them

# General Requirements
 - The application must remain usable when the device is offline.
 - Network operations must not block the UI thread.
 - Network failures must be handled gracefully.
 - Database failures must be reported to the user (eg. SQLite errors).
 - Long-running operations must expose their current state.
 - Destructive operations must require user confirmation.
 - The application must preserve locally available databases when network operations fail.
 - The application must not modify a local database unless the user explicitly requests an update or refresh.
 - Read-only databases must not be modified.
 - Modification controls should be hidden for read-only databases where possible.
 - If a modification control cannot be hidden, it must be disabled.
 - The application must not attempt a write operation against a read-only database.
 - if network is disabled network related widgets, like buttons should be not visible