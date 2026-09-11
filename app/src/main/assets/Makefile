
jsons:
	rm -f *.json
	
	wget https://github.com/rumca-js/awesome-database-top/raw/refs/heads/main/internet.db.zip
	unzip internet.db.zip
	rm -f internet.db.zip
	rm -f poetry.lock
	 
	poetry run python dbupdate.py --db internet.db --delete-no-votes --delete-redundant --delete-age --obfuscate
	poetry run python db2json.py --file-name-prefix places --db internet.db
	rm poetry.lock

table:
	rm -f table.db
	touch table.db
	poetry run python dbupdate.py --create-tables --db table.db
	rm poetry.lock
