Feature: receive event

  Background: 
  	Given exec script
  	  """
  	  CREATE KEYSPACE IF NOT EXISTS test_keyspace
  	  WITH REPLICATION = {'class' : 'NetworkTopologyStrategy','datacenter1' : 2};
  		"""
  	And exec script
  		"""
  	  CREATE TABLE IF NOT EXISTS test_keyspace.test_counter_history ( 
				id UUID,
				date timestamp,
				user text,
				application text,
				quantity counter,
				PRIMARY KEY ((id), date, user, application)
			) WITH cdc=true;
  	  """

  Scenario: increment one counter
    When exec script
      """
       UPDATE test_keyspace.test_counter_history
       SET quantity = quantity + 3
       WHERE id = 27a7c6c2-9ada-4b97-a079-f440e7774f0b AND
             date = '2023-12-01 12:00' AND
             user = 'jean.dupond@gmail.com' AND
             application = 'ap1';
      """
    Then receive 1 event
    And last message is
     """
      {
          "quantity": 3,
          "id": "27a7c6c2-9ada-4b97-a079-f440e7774f0b"
      }
      """

  Scenario: increment again
    When exec script
      """
       UPDATE test_keyspace.test_counter_history
       SET quantity = quantity + 2
       WHERE id = 27a7c6c2-9ada-4b97-a079-f440e7774f0b AND
             date = '2023-12-01 13:00' AND
             user = 'jean.dupond@gmail.com' AND
             application = 'ap1';
      """
    Then receive 1 event
    And last message is
     """
      {
          "quantity": 2,
          "id": "27a7c6c2-9ada-4b97-a079-f440e7774f0b"
      }
      """

