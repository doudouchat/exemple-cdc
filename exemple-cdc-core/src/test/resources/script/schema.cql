CREATE KEYSPACE IF NOT EXISTS test_keyspace
  WITH REPLICATION = {
   'class' : 'NetworkTopologyStrategy',
   'datacenter1' : 1
};

USE test_keyspace;

CREATE TABLE IF NOT EXISTS test_event( 
	id UUID,
	date timestamp,
	user text,
	data text,
	event_type text,
	application text,
	version text,
	PRIMARY KEY ((id), date)
) WITH cdc=true;

CREATE TABLE IF NOT EXISTS test_with_composite_key_event( 
	id1 UUID,
	id2 UUID,
	date timestamp,
	user text,
	data text,
	event_type text,
	application text,
	version text,
	PRIMARY KEY ((id1, id2), date)
) WITH cdc=true;

CREATE TABLE IF NOT EXISTS test_with_counter_history(
	id UUID,
	date timestamp,
	user text,
	application text,
	quantity counter,
	PRIMARY KEY ((id), date, user, application)
) WITH cdc=true;

CREATE TABLE IF NOT EXISTS test_other( 
	id UUID,
	PRIMARY KEY (id)
);