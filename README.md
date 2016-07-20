Voygage Passenger Splits
====================

Service reads data from Advance Passenger Information (API) which is posted to an S3 bucket,
performs some data crunching, and then provides a rest query endpoint so that other parts of DRT can ask

?) What is the paxSplit (passenger desk distribution) for flight BA123 @  2016-06-03 14:45 (i.e.
  http://service:port/flight-pax-splits/port-LHR/BA123/scheduled-arrival-time-20160603T1445

?) What are the paxSplits for flights a port, between 20160612T1231 and 201607T1830 (i.e.
  http://service:port/flight-pax-splits/port-LHR?from=20160612T1231&to=2016071830

