ARG VARIANT=bookworm
FROM mcr.microsoft.com/devcontainers/java:1-21-${VARIANT}

ARG MAVEN_VERSION=3.9.11
ARG NODE_VERSION=22

ENV MAVEN_HOME=/usr/local/apache-maven \
    PATH=/usr/local/apache-maven/bin:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        tar \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o /tmp/maven.tar.gz \
    && tar -xzf /tmp/maven.tar.gz -C /usr/local \
    && ln -sfn /usr/local/apache-maven-${MAVEN_VERSION} /usr/local/apache-maven \
    && rm /tmp/maven.tar.gz

# Install Node.js
RUN curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*
