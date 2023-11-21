CREATE KEYSPACE IF NOT EXISTS test_keyspace
  WITH REPLICATION = {
   'class' : 'NetworkTopologyStrategy',
   'datacenter1' : 1
};

USE test_keyspace;

CREATE TABLE IF NOT EXISTS test_event( 
	id UUID,
	date timestamp,
	local_date date,
	data text,
	event_type text,
	application text,
	version text,
	PRIMARY KEY ((id), date)
) WITH cdc=true;

CREATE TABLE IF NOT EXISTS other_event( 
	id int,
	date timestamp,
	local_date date,
	data text,
	event_type text,
	application text,
	version text,
	PRIMARY KEY ((id), date)
) WITH cdc=true;