
jsons:
	#rm *.json
	
	#wget https://github.com/rumca-js/awesome-database-top/raw/refs/heads/main/internet.db.zip
	#unzip internet.db.zip
	#rm internet.db.zip
	 
	poetry run python dbupdate.py --db internet.db --delete-no-votes --delete-redundant --delete-age --obfuscate
	poetry run python db2json.py --file-name-prefix places --db internet.db

table:
	rm table.db
	touch table.db
	poetry run python dbupdate.py --create-tables --db table.db
