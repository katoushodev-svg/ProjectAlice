import 'package:flutter/material.dart';

import '../conversation/presentation/screen/conversation_screen.dart';
import 'theme/alice_theme.dart';

class AliceApp extends StatelessWidget {
  const AliceApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Alice',
      theme: AliceTheme.darkTheme,
      darkTheme: AliceTheme.darkTheme,
      themeMode: ThemeMode.dark,
      home: const ConversationScreen(),
    );
  }
}
