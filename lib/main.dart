import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final controller = TextEditingController();

  @override
  void initState() {
    TunChannel.instance
        .loadHostPort()
        .then((value) => controller.text = value ?? '');
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            TextField(
              decoration: const InputDecoration(
                hintText: '127.0.0.1:1',
              ),
              textAlign: TextAlign.center,
              controller: controller,
            ),
            ElevatedButton(
              onPressed: _startVPN,
              child: const Text('Start VPN'),
            ),
            ElevatedButton(
              onPressed: _stopVPN,
              child: const Text('Stop VPN'),
            ),
          ],
        ),
      ),
    );
  }

  void _startVPN() => TunChannel.instance.startVPN(controller.text);

  void _stopVPN() => TunChannel.instance.stopVPN();
}

class TunChannel {
  TunChannel._();
  static final instance = TunChannel._();
  final _channel = const MethodChannel('tun_proxy');
  void startVPN(String host) async {
    await _channel.invokeMethod('startVPN', host);
  }

  void stopVPN() async {
    await _channel.invokeMethod('stopVPN');
  }

  Future<String?> loadHostPort() async {
    return await _channel.invokeMethod('loadHostPort');
  }
}
