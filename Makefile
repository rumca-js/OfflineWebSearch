.PHONY: clean build-debug

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
	rm -rf app/release

build-debug:
    ./gradlew assembleDebug -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m"

# ~/.gradle/gradle.properties file
# MYAPP_RELEASE_STORE_FILE=../my-release-key.jks
# MYAPP_RELEASE_STORE_PASSWORD=your_keystore_password
# MYAPP_RELEASE_KEY_ALIAS=your_key_alias
# MYAPP_RELEASE_KEY_PASSWORD=your_key_password

build-release:
    ./gradlew assembleRelease -Dorg.gradle.jvmargs="-Xmx1500m -XX:MaxMetaspaceSize=512m" -Dkotlin.daemon.jvmargs="-Xmx1500m" -Dkotlin.compiler.execution.strategy="in-process"