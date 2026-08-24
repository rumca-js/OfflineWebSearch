import argparse

from linkarchivetools.dbfilter import DbFilter
from linkarchivetools.db2json import Db2JSON
from linkarchivetools.dbanalyzer import DbAnalyzer
from linkarchivetools.utils.reflected import ReflectedTable
from linkarchivetools.model import DbConnection


def parse():
    parser = argparse.ArgumentParser(description="filtering program")
    parser.add_argument("--db", help="DB to be processed")
    parser.add_argument("--output-dir", help="Directory to be created")
    parser.add_argument("--output-file", help="Output file")
    parser.add_argument("--file-names", help="File names")
    parser.add_argument("--bookmarked", action="store_true", help="Filtering by bookmarks. Entries that are bookmarked are left in")
    parser.add_argument("--votes", action="store_true", help="Filtering by votes. Entries with votes are maintained.")
    parser.add_argument("--redundant", action="store_true", help="Removes entries that are redundant")
    parser.add_argument("--user-data", action="store_true", help="Removes user data")
    parser.add_argument("--jsons", action="store_true", help="exported to JSONs")
    parser.add_argument("-v", "--verbosity", help="Verbosity level")
    
    args = parser.parse_args()

    return parser, args


def main():
    temporary_file = "tmp.db"

    parser, args = parse()
    if not args.db:
        print("Please specify database")
        return

    if args.output_file:
        temporary_file = args.output_file

    #analyzer = DbAnalyzer(input_db = args.db)
    #analyzer.print_summary()

    print("Filtering")
    filter = DbFilter(input_db=args.db,output_db=temporary_file)
    if args.votes:
        filter.filter_votes()
    if args.bookmarked:
        filter.filter_bookmarked()
    if args.redundant:
        filter.filter_redundant()
    if args.user_data:
        filter.truncate_no_users()

    filter.close()
    print("Filtering DONE")

    #analyzer = DbAnalyzer(input_db = temporary_file)
    #analyzer.print_summary()

    if args.jsons:
        print("Writing JSONS")
        json = Db2JSON(input_db = temporary_file, output_dir=args.output_dir, format=args.file_names, rows_max=1000)
        #if not json.is_valid():
        #    return

        json.convert()
        json.close()

        print("Writing JSONS DONE")
    else:
        connection = DbConnection(temporary_file)
        table = ReflectedTable(engine=connection.engine, connection=connection.connection)
        table.vacuum()
        table.close()
        connection.close()

main()
