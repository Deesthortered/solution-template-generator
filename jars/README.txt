* Ubuntu
mvn install:install-file -Dfile=./data-3.6.2PE-SNAPSHOT.jar -DgroupId=org.thingsboard.common -DartifactId=data -Dversion=3.6.2PE-SNAPSHOT -Dpackaging=jar -DgeneratePom=true
mvn install:install-file -Dfile=./rule-engine-api-3.6.2PE-SNAPSHOT.jar -DgroupId=org.thingsboard.rule-engine -DartifactId=api -Dversion=3.6.2PE-SNAPSHOT -Dpackaging=jar -DgeneratePom=true
mvn install:install-file -Dfile=./rule-engine-components-3.6.2PE-SNAPSHOT.jar -DgroupId=org.thingsboard.rule-engine -DartifactId=rule-engine-components -Dversion=3.6.2PE-SNAPSHOT -Dpackaging=jar -DgeneratePom=true

* Windows
mvn install:install-file "-Dfile=./data-3.6.2PE-SNAPSHOT.jar" "-DgroupId=org.thingsboard.common" "-DartifactId=data" "-Dversion=3.6.2PE-SNAPSHOT" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=./rule-engine-api-3.6.2PE-SNAPSHOT.jar" "-DgroupId=org.thingsboard.rule-engine" "-DartifactId=api" "-Dversion=3.6.2PE-SNAPSHOT" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=./rule-engine-components-3.6.2PE-SNAPSHOT.jar" "-DgroupId=org.thingsboard.rule-engine" "-DartifactId=rule-engine-components" "-Dversion=3.6.2PE-SNAPSHOT" "-Dpackaging=jar" "-DgeneratePom=true"
