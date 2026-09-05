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
 - Date of creation database
 - Date of last database refresh/update

Possible database states include:
 - DOWNLOADING
 - UNPACKING
 - READY
 - FAILED

# Views
App should contain on bottom selection of main views:
 - Search
 - Sources
 - Options

Screens:
 - EntryListScreen - Provides the search interface. Shows search results.
 - EntryDetailScreen - Shows details of a selected entry.
 - EntryEditScreen - Adds or edits an entry.
 - EntryStatusScreen - Checks the HTTP status of an entry URL.
 - EntryPreviewScreen - Fetches and displays the current web page data.
 - OptionsScreen - Configures databases and application settings.
 - DatabasesScreen - To be removed
 - DatabaseScreen - Shows information and actions for a database.
 - SourceScreen - Shows information and actions for a source.
 - SourceEditScreen - Adds or edits a source.
 - SourceUrlEditPreviewScreen - Preview screen shown when adding a source by URL. Fetches RSS feed data to pre-populate fields.
 - SourcesScreen - Shows the list of configured sources.
 - AboutScreen - Shows information about the application.
 - VisitedEntriesScreen - To be removed
 - ReadLaterScreen - To be removed

## EntryListScreen
 - Entries screen is scrollable (search widget, entry results, pagination controls)
 - Provides search widget with full width text input
 - Search widget is followed with "Search" button and a button to select "filters" for search results.
 Filters are read from SQLite table, but if it nos available by default by Date published filter should be used. Always some filter need to be applied
   -- "Visited" filter - entry results should visited entries
   -- "Read Later" filter - entry results should entries marked for read later
   -- "by Date published" - shows entries, but with this order applied
   -- "by Votes" - shows entries, but with this order applied
   -- "by Visits" - shows entries, but with this order applied
 - button to select filter is small, and on the right side of "Search" button
 - Search supports advanced search capabilities (like SQLite syntax).
 - The list supports loading additional results.
 - Search results support page navigation (or dynamic loading of next / prev elements)
 - page navigation elements (prev, next button) are in scrollable area, after search results
 - after page navigation add new result button should be present
 - Selecting an entry opens EntryDetailScreen.
 - Entry results use pull to refresh, fetches data again
 - Entry results shall be refreshed when necessary
    -- when "Read Later" filter is applied, and "Read Later" table changes
    -- when sources are successfully refreshed
    -- when entry is deleted from EntryDetailScreen
 - swipe left performs "next" pagination on results
 - swipe right performs "previous" pagination on results

### EntryItem - row in EntryListScreen
There are several list styles:
 - Gallery (emphasis on thumbnail, should look like YouTube or TikTok)
 - Standard (similar to gallery, but thumbnail is smaller, and on the left, should remind Feed readers
 - Search engine (emphasis on title, and showing actual link. Should look like search engine results)

 Entry Item shall display:
 - entry.thumbnail or source.favicon
 - title
 - date of publish
 - source.title

 If thumbnail is not available, then it should be skipped.

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
 - Thumbnail, or video playback frame (if thumbnail is available, if icons are enabled in configuration)
 - Title.
 - Link.
 - Date of publish
 - Tags, followed with pen icon to edit tags
 - Source information
 - Buttons for some operations like: "Check status", "Add vote"
   -- vote allows user to input digit between -100 and 100
 - Description. User should be able to select text in it. Should make https:// links a real links
 - Other entry metadata.
 - Entry transition pane. The pane shows entries that the user previously visited from the current entry. Entry transitions are stored in UserEntryTransitionHistory.
 - back button returning to EntryListScreen should not refresh list, nor should scroll to the top, as it does not modify Entries, or results. It should if read later were changed and EntryList is with read later filter, or if EntryList is with sort by Visits

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

## SourcesScreen
 - Provides bar on top with buttons: add, fetch
 - Selecting source opens SourceScreen.
 - Provides search widget, similar to EntryListScreen, it should be scrollable
 - Similarly to EntryListScreen should contain "Search" button with a button to apply filter (order by title, or fetch time)
 - Filters should be by Url, Title, Fetch time. By default by Url filter should be applied. A filter always need to be applied
 - Probably it would have to be a different search widget implementation from EntryListScreen. These can share same base class though
 - Source fetch means that body of page is fetched, should be RSS, entries are read from it, and inserted into linkdatamodel table.
 - If possible fetch/refresh button should spin if sources are being refreshed
 - just as EntryListScreen, pull to refresh sources

## SourceScreen
 - Displays information about a source.
 - Provides a top bar with the following actions:
    -- Fetch / Update
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
 - Provides input fields for source title, URL, and enabled status via shared SourceFormPane component.
 - Saves changes to the sourcedatamodel table in the active database.
 - Only accessible when the active database is writable.

## SourceUrlEditPreviewScreen
 - Shown when the user taps the Add (+) button in SourcesScreen.
 - Fetches RSS/feed data from the given URL using Url.kt in the background.
 - Auto-populates title from the feed once loaded.
 - Shares same form fields (title, URL, enabled) with SourceEditScreen via SourceFormPane.
 - Displays additional feed info: HTTP status code, Content-Type, Content-Length, and RSS entry count.
 - On success, inserts the new source into the sourcedatamodel table.

## OptionsScreen
- Displays the configured databases (list).
- Provides button to add new databases: from preselected list, new empty database, from local file, from url
- preselected list button navigates to DatabasePreselectedListScreen
- Database settings should be in DatabaseScreen, not here
- No advanced database operation should be accessible here
- Database are in pill-like widget
- If database is active, then it should have primary color frame. If android does not provide "primary" color for android app theme, then define it somewhere in app configuration
- Each database should contain indicator: state, if active or not
- Each database should contain buttons to: refresh (if it is from the internet), remove (should not contain badge, nor combo indicating it is active)
- single tap on database makes it's active
- long press transitions to DatabaseScreen
- Provides button to navigate to AboutScreen.

## DatabaseScreen
- Displays information if it is currently active
- Provides a top bar with the following actions:
  -- Edit.
  -- Share.
  -- Fetch database again
  -- Remove.
- Displays the current database state (read only?).
- Displays the database name.
- Displays the database type.
- Displays the local file information.
- Displays the remote URL, if applicable.
- Displays the date of creation.
- Displays the date of last refresh/update.
- Displays the number of rows in relevant tables.
- Provides an action to clear the search history.
- Provides an action to clear the user's visited entries.
- Provides an action to clear the user's read later entries.
- Provides an action to clear the social data.
- implement pull to refresh data about database

## DatabasePreselectedListScreen
- provides filter widget (user can provide text input to filter databases)
- loads list of databases from github
- shows databases in pill shaped rows
- user can select database by a single tap. This action returns to OptionsScreen

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
 - Repositories implement `RepositoryInterface` for common operations such as clearing table contents and deleting records by ID (`deleteById`).
 - For example, SourceRepository provides access to the sourcedatamodel table.
 - Database access should not be performed directly from UI screens.
 - UI screens should access data through the appropriate repository or data-layer interface.

# Background workers
Process of workers should be visible on some screens.
For example source refresh should make refresh button to be spinning, if possible
Database refresh should make database refresh buttons to be spinning, if possible

## Source refresh
 - there is a background task that can refresh sources
 - It would be best if it could accept new sources to refresh
 - disabled sources should be skipped
 - sources that were fetched eariler than hour since source.date\_fetch should be skipped
 - should use order of sources by last fetched (or that never have been fetched)
 - should change date\_fetch, but only after attempt to read source has been made

## Database update
 - it would be best if it could accept new databases to fetch
 - multiple databases should not be processed in parallel. They should be handled sequentially

## Entry enrichment worker
Used to enhance entry properties (updates title, status\_code, whatever can be updated)

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
