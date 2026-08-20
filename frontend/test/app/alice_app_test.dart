import 'package:flutter_test/flutter_test.dart';
import 'package:alice/app/alice_app.dart';

void main() {
  testWidgets('AliceApp shows Alice text', (WidgetTester tester) async {
    await tester.pumpWidget(const AliceApp());
    await tester.pumpAndSettle();
    expect(find.text('Alice'), findsOneWidget);
  });
}
