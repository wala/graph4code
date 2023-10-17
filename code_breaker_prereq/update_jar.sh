cd ../code_breaker_prereq
mvn install:install-file -Dfile=target/CodeBreakerPrereq-0.0.1-SNAPSHOT.jar -DgroupId=CodeKnowledgeGraph -DartifactId=CodeBreakerPrereq -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
cd ../code_breaker_prereq_py3/
mvn install:install-file -Dfile=target/CodeBreakerPrereqPy3-0.0.1-SNAPSHOT.jar -DgroupId=CodeKnowledgeGraph -DartifactId=CodeBreakerPrereqPy3 -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar
cd ../code_breaker
mvn clean install -DskipTests
cd ../code_breaker_py3/
mvn clean install -DskipTests
