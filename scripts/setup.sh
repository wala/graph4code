#!/bin/bash

mvn install:install-file -Dfile=../code_breaker_prereq/target/CodeBreakerPrereq-0.0.1-SNAPSHOT.jar -DpomFile=../code_breaker_prereq/CodeBreakerPrereq-0.0.1-SNAPSHOT.pom

mvn install:install-file -Dfile=../code_breaker_base/target/CodeBreakerBase-0.0.1-SNAPSHOT.jar -DpomFile=../code_breaker_base/CodeBreakerBase-0.0.1-SNAPSHOT.pom

mvn install:install-file -Dfile=../code_breaker_prereq_py2/target/CodeBreakerPrereqPy2-0.0.1-SNAPSHOT.jar -DpomFile=../code_breaker_prereq_py2/CodeBreakerPrereqPy2-0.0.1-SNAPSHOT.pom

mvn install:install-file -Dfile=../code_breaker_prereq_py3/target/CodeBreakerPrereqPy3-0.0.1-SNAPSHOT.jar -DpomFile=../code_breaker_prereq_py3/CodeBreakerPrereqPy3-0.0.1-SNAPSHOT.pom

pushd ../code_breaker
mvn clean install -DskipTests
popd

pushd ../code_breaker_py2
mvn clean install -DskipTests
popd

pushd ../code_breaker_py3
mvn clean install -DskipTests
popd
