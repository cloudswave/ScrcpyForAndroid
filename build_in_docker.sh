docker run --rm -v `pwd`:/project -v "$HOME/.dockercache/gradle":"/root/.gradle" \
-e JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
mingc/android-build-box \
bash -c 'cd /project; ./gradlew assembleDebug'