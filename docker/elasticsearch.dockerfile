FROM docker.elastic.co/elasticsearch/elasticsearch:5.4.3

ENV discovery.type=single-node
ENV cluster.name=es-catalog
ENV xpack.security.enabled=false
ENV http.host=0.0.0.0
ENV transport.host=0.0.0.0
ENV network.host=0.0.0.0

VOLUME /usr/share/elasticsearch/data

EXPOSE 9200 9300
