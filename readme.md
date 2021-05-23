# Run The Project
The main object under com.kaizo.assignment will start a simple server and 
bind it to local host and port 8080. It uses persistenc actors and leveldb. State is stored under target/kaizo. 
This folder should be created in the root of the project.

# Endpoints
The following endpoints are exposed

### Create Stream:  /kaizo/v1/addcustomerstream
This endpoint takes two parameters:
- clientName: a unique identifier for a client
- token: the zen desk access token

It will spawn an actor that will ping the zendesk endpoint once every 6 seconds

http://localhost:8080/kaizo/v1/addcustomerstream?clientName=client1&token=123

```json
{
    "message": "Stream client2 succesfully created"
}
```

### Get lag of stream: /kaizo/v1/streamlag
This endpoint takes one parameter:
- clientName: a unique identifier for a client

It will return a json with the current status of the stream

http://localhost:8080/kaizo/v1/streamlag?clientName=client1
```json
{
    "endOfStream": true,
    "endTime": 1621004578,
    "nextPage": "https://d3v-kaizo.zendesk.com/api/v2/incremental/tickets.json"
}
```
### Get currently active client streams: /kaizo/v1/activeclients
This endpoint takes no parameters and will return a string with the names of currently active streams.

http://localhost:8080/kaizo/v1/activeclients
```json
{
    "message": "client1 client2"
}
```

### Stop client stream: /kaizo/v1/stopclientstream
This endpoint takes one parameter:
- clientName: a unique identifier for a client

It will return a json with the name of the stopped stream: 

http://localhost:8080/kaizo/v1/stopclientstream?clientName=client1
```json
{
    "message": "Stopped client1 stream"
}
```

## Persistence
The project uses leveldb as a persistent store for actor state. This is only implemented for the RestClientActor.
The state is stored under target/kaizo. For the project to run with persistence, this folder should be created.

## Issues
- F*%^ing APIRateLimitExceeded errors. The rest client is scheduled to ping the endpoint every 6 seconds for 
  a given client stream, ad tries to get the max number of tickets. If I read the documents correctly, this should be fine, so clearly there is 
  something I am missing here...
  
- Better handling of wrong tokens. If a stream gets created with the wrong token, it will just keep spitting out error 
  messages until stopped via the stopclientstream endpoint 
  
- Testing examples, could add later if that is not cheating
- start_time and end_time. Use the returned end_time as the start_time for the next api call, however this keeps 
  returning two tickets updated at exactly that end_time. However in the documentation it says to use the returned 
  end_time from the time based pagination as the next start_time. Not sure what I am doing wrong here...  
  
- Not tested with two or more streams, so hopefully the actor based approach will work :)
- Better logging and info/warn/error messages
- Spins up a new actor to process every ResponseEntity, and then stops that actor after processing, this might be overkill...
- Could return the lag state in a bit more readable format
- Better checking of return messages from the zendesk endpoint
- nextpage field in the StreamState case class is probably redundant
- Better configuration of the application and individual streams, such as 
  being able to set the max number of tickets returned.