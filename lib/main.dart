import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
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
      home: const MyHomePage(title: 'Tun Proxy'),
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
  final controllerGetAPI = TextEditingController();
  final controllerPathAPI = TextEditingController();
  final controllerHeaders = TextEditingController();
  final controllerResult = TextEditingController();

  bool _isRunning = false;
  String? error;
  bool _isLoading = false;

  @override
  void initState() {
    TunChannel.instance.isRunning.then(
      (value) => setState(() {
        _isRunning = value;
      }),
    );
    TunChannel.instance
        .loadHostPort()
        .then((value) => controller.text = value ?? '');
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Scaffold(
          appBar: AppBar(
            backgroundColor: Theme.of(context).colorScheme.inversePrimary,
            title: Text(widget.title),
          ),
          body: Center(
            child: SingleChildScrollView(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  if (error != null)
                    Text(error!, style: const TextStyle(color: Colors.red)),
                  TextField(
                    decoration: const InputDecoration(
                      hintText: '127.0.0.1:1',
                    ),
                    textAlign: TextAlign.center,
                    controller: controller,
                  ),
                  const SizedBox(height: 10),
                  if (!_isRunning)
                    ElevatedButton(
                      onPressed: _startVPN,
                      child: const Text('Start VPN'),
                    )
                  else ...[
                    ElevatedButton(
                      onPressed: _stopVPN,
                      child: const Text('Stop VPN'),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      decoration: const InputDecoration(
                        hintText: 'http://127.0.0.1:1',
                      ),
                      textAlign: TextAlign.center,
                      controller: controllerGetAPI,
                    ),
                    TextField(
                      decoration: const InputDecoration(
                        hintText: '/product',
                      ),
                      textAlign: TextAlign.center,
                      controller: controllerPathAPI,
                    ),
                    TextField(
                      decoration: const InputDecoration(
                        hintText: '''{"aaa":"bbb"}''',
                      ),
                      maxLines: 4,
                      minLines: 3,
                      textAlign: TextAlign.center,
                      controller: controllerHeaders,
                    ),
                    const SizedBox(height: 10),
                    ElevatedButton(
                      onPressed: _fetch,
                      child: const Text('Get API'),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      readOnly: true,
                      minLines: 3,
                      maxLines: 4,
                      textAlign: TextAlign.center,
                      controller: controllerResult,
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
        if (_isLoading)
          const Positioned.fill(
            child: Center(
              child: CircularProgressIndicator(),
            ),
          ),
      ],
    );
  }

  void _startVPN() async {
    final isIpV4 =
        await TunChannel.instance.isValidIPv4Address(controller.text);
    setState(() => error = !isIpV4 ? 'host is not ipv4' : null);
    if (!isIpV4) return;
    final value = await TunChannel.instance.startVPN(controller.text);
    setState(() => _isRunning = value);
    if (value) {
      controllerGetAPI.text = 'http://${controller.text}';
    }
  }

  void _stopVPN() => TunChannel.instance
      .stopVPN()
      .then((value) => setState(() => _isRunning = false));

  void _fetch() async {
    setState(() => _isLoading = true);
    final headersStr = controllerHeaders.text.trim();
    Map<String, dynamic> headers = {};
    if (headersStr.isNotEmpty) {
      final hMap = jsonDecode(headersStr);
      if (hMap != null && hMap is Map) {
        hMap as Map<String, dynamic>;
        headers.addAll(hMap);
      }
    }

    final dio = Dio()
      ..options.headers = headers
      ..options.baseUrl = controllerGetAPI.text;
    final response = await dio.get(controllerPathAPI.text.trim());
    controllerResult.text = jsonEncode(response.data);
    setState(() => _isLoading = false);
  }
}

class TunChannel {
  TunChannel._();
  static final instance = TunChannel._();
  final _channel = const MethodChannel('tun_proxy');

  final channelFlutter = const MethodChannel('tun_proxy/fl')
    ..setMethodCallHandler((call) async {
      switch (call.method) {
        case 'startResult':
          {
            _completerStart.complete(call.arguments as bool);
          }
      }
    });

  static late Completer<bool> _completerStart;

  Future<bool> startVPN(String host) async {
    _completerStart = Completer<bool>();
    await _channel.invokeMethod('startVPN', host);
    return await _completerStart.future;
  }

  Future<bool> isValidIPv4Address(String host) async =>
      await _channel.invokeMethod('isValidIPv4Address', host);

  Future<void> stopVPN() async {
    await _channel.invokeMethod('stopVPN');
  }

  Future<String?> loadHostPort() async {
    return await _channel.invokeMethod('loadHostPort');
  }

  Future<bool> get isRunning async {
    return (await _channel.invokeMethod('isRunning')) ?? false;
  }
}
