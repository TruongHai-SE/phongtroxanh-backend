FROM alpine:latest
RUN apk add --no-cache postgresql16 postgresql16-contrib postgis su-exec tzdata bash
ENV PGDATA=/var/lib/postgresql/data
RUN mkdir -p /var/lib/postgresql/data /run/postgresql /docker-entrypoint-initdb.d \
    && chown -R postgres:postgres /var/lib/postgresql /run/postgresql /docker-entrypoint-initdb.d
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/entrypoint.sh && chmod +x /usr/local/bin/entrypoint.sh
EXPOSE 5432
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["postgres"]
