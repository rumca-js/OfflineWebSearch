tag:
	git tag -f 0.0.9
	git push github 0.0.9 -f

test:
	./gradlew test

# how to update
# - write /fastlane/metadata/android/en-US/changelogs/ file with a version code .txt file
# - build.gradle.kts: bump version code and version number
# - push to github
# - create tag in github
# - generate signed apk
# - upload release
