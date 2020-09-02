#
# Copyright © 2019 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

# Dockerfile for building container for building CDAP.
# It populate maven cache with dependencies needed for building CDAP.
FROM maven:3-jdk-8 AS build
ENV MAVEN_OPTS -Xmx4096m
WORKDIR /cdap/maven/
COPY . /cdap/maven/
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline -B -V -P template,dist,k8s
