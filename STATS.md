# TinyWeb Statistics

TinyWeb provides a built-in statistics capability that allows you to monitor and analyze the performance of your web server. This feature is useful for understanding the behavior of your application and identifying potential bottlenecks.

## Key Features

- **Request Duration**: Track the total time taken to process each request.
- **Endpoint Statistics**: Monitor the performance of individual endpoints, including the time taken to execute and the response status.
- **Filter Statistics**: Analyze the performance of filters applied to requests, including the time taken and the result of each filter.

## How It Works

TinyWeb automatically collects statistics for each request processed by the server. These statistics include:

- **Path**: The URL path of the request.
- **Duration**: The total time taken to process the request, including all filters and the endpoint.
- **Endpoint**: The endpoint that handled the request, or "unmatched" if no endpoint was found.
- **Status**: The HTTP status code returned to the client.
- **Filters**: A list of filters applied to the request, including the path, result, and duration of each filter.

## Accessing Statistics

To access the collected statistics, you can override the `recordStatistics` method in your TinyWeb server implementation. This method is called after each request is processed, and it receives a map of statistics that you can log, store, or analyze as needed.

Example:
```java
@Override
protected void recordStatistics(String path, Map<String, Object> stats) {
    // Log or store the statistics
    System.out.println("Request statistics: " + stats);
}
```

By customizing the `recordStatistics` method, you can integrate TinyWeb's statistics capability with your existing monitoring and logging infrastructure.

## Use Cases

- **Performance Monitoring**: Identify slow endpoints and optimize them for better performance.
- **Error Tracking**: Monitor the frequency and types of errors occurring in your application.
- **Filter Analysis**: Understand the impact of filters on request processing time and optimize them as needed.

TinyWeb's statistics capability provides valuable insights into the performance and behavior of your web application, helping you to maintain a high-quality user experience.
