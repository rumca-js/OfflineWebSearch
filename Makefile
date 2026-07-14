.PHONY: clean

tag:
	git tag -f 0.0.9
	git push github 0.0.9 -f

test:
	./gradlew test

# how to update
# - /app/build.gradle.kts: bump version code and version number
# - write /fastlane/metadata/android/en-US/changelogs/ file with a version code .txt file
# - push to github
# - create tag in github
# - checkout tag. Make sure there is no stray file modified
# - make clean
# - generate signed apk
# - upload release
#

clean: 
	-./gradlew clean
	rm -rf .gradle/
	find . -type d -name "build" -exec rm -rf {} +
