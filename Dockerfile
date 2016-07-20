FROM quay.io/ukhomeofficedigital/openjdk8:v1.0.0

ENV SCALA_VERSION 2.11.7
ENV SBT_VERSION 0.13.11

# Install Scala
## Piping curl directly in tar
RUN \
  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo 'export PATH=~/scala-$SCALA_VERSION/bin:$PATH' >> /root/.bashrc

# Install sbt
RUN curl https://bintray.com/sbt/rpm/rpm | tee /etc/yum.repos.d/bintray-sbt-rpm.repo

RUN yum install -y sbt

COPY . /root/appbuild

WORKDIR /root/appbuild/

RUN sbt compile

CMD sbt run

