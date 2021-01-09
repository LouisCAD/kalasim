## How to do a release?

1. Make sure to increase version number in [setup](userguide/docs/setup.md) and [build.gradle.kts](../build.gradle.kts)


2. Do the release
```bash
# adjust to te path of your working copy
export KALASIM_HOME=/c/brandl_data/projects/scheduling/kalasim


## Increment version in readme, gradle, example-poms and

cd $KALASIM_HOME

./gradlew check


trim() { while read -r line; do echo "$line"; done; }
kalasim_version='v'$(grep '^version' ${KALASIM_HOME}/build.gradle.kts | cut -f3 -d' ' | tr -d '"' | trim)

echo "new version is $kalasim_version !"

if [[ $kalasim_version == *"-SNAPSHOT" ]]; then
  echo "ERROR: Won't publish snapshot build $kalasim_version}!" 1>&2
  exit 1
fi


git status
git commit -am "${kalasim_version} release"
#git diff --exit-code  || echo "There are uncomitted changes"

git tag "${kalasim_version}"

git push origin 
git push origin --tags


### Build and publish the binary release to jcenter
gradle install

# careful with this one!
gradle bintrayUpload

#For released versions check:
#- https://bintray.com/holgerbrandl/github/kalasim
#- https://jcenter.bintray.com/de/github/kalasim
```