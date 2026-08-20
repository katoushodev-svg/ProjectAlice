import 'package:flutter/material.dart';

class AliceApp extends StatelessWidget {
  const AliceApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Alice',
      home: Scaffold(body: Center(child: Text('Alice'))),
    );
  }
}
