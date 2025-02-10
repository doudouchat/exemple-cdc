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
    Given stop agent
    And reload agent
    When exec script
      """
       INSERT INTO test_keyspace.test_event (id, date, application, version, event_type, data, user) VALUES (
                    5f8839a9-b3ef-4818-a43c-d3c7a709ca14,
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
          "id": "5f8839a9-b3ef-4818-a43c-d3c7a709ca14"
      }
      """

