FROM anapsix/alpine-java:8_jdk_unlimited

ARG jarfile=celos-ci.jar

COPY $jarfile /opt/celos/celos-ci.jar

VOLUME /etc/celos

ENV PORT=80 \
    STATE_DB_PATH=/etc/celos/state-db \
    WORKFLOWS_PATH=/etc/celos/workflows \
    DEFAULTS_PATH=/etc/celos/defaults

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/opt/celos/celos-ci.jar"]

CMD [""]
