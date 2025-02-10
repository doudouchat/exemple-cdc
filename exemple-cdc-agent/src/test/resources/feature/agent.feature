Feature: receive event

  Background: 
  	Given exec script
  	  """
  	  CREATE KEYSPACE IF NOT EXISTS test_keyspace
  	  WITH REPLICATION = {'class' : 'NetworkTopologyStrategy','datacenter1' : 2};
  		"""
  	And exec script
  		"""
  	  CREATE TABLE IF NOT EXISTS test_keyspace.test_event( 
				id UUID,
				date timestamp,
				user text,
				data text,
				event_type text,
				application text,
				version text,
				PRIMARY KEY ((id), date)
			) WITH cdc=true;
  	  """

  Scenario: receive one event
    When exec script
      """
       INSERT INTO test_keyspace.test_event (id, date, application, version, event_type, data, user) VALUES (
                    e143f715-f14e-44b4-90f1-47246661eb7d,
                    '2023-12-01 12:00',
                    'app1',
                    'v1',
                    'CREATE_ACCOUNT',
                    '{
                        "email": "jean.dupond@gmail.com",
                        "lastname":  "Dupond"
                    }',
                   'jean.dupond'
                   );
      """
    Then receive 1 event
    And last message is
     """
      {
          "email": "jean.dupond@gmail.com",
          "lastname": "Dupond",
          "id": "e143f715-f14e-44b4-90f1-47246661eb7d"
      }
      """

  Scenario: receive second event
    When exec script
      """
       INSERT INTO test_keyspace.test_event (id, date, application, version, event_type, data, user) VALUES (
                    4ad62161-82ca-4769-9b5b-9147437a99db,
                    '2023-12-01 12:00',
                    'app1',
                    'v1',
                    'CREATE_ACCOUNT',
                    '{
                        "email": "jean.dupond@gmail.com",
                        "lastname":  "Dupond"
                    }',
                   'jean.dupond'
                   );
      """
    Then receive 1 event
    And last message is
     """
      {
          "email": "jean.dupond@gmail.com",
          "lastname": "Dupond",
          "id": "4ad62161-82ca-4769-9b5b-9147437a99db"
      }
      """

