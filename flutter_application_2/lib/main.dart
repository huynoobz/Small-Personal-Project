import 'package:flutter/material.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:system_info/system_info.dart';
import 'package:disk_space_plus/disk_space_plus.dart';
import 'package:flutter_performance_pulse/flutter_performance_pulse.dart';
import 'dart:async';

String cpuUsage = "0";

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize the performance monitor
  await PerformanceMonitor.instance.initialize(
    config: const MonitorConfig(
      showMemory: true,
      showLogs: true,
      trackStartup: true,
      interceptNetwork: true,
      fpsWarningThreshold: 45,
      enableNetworkMonitoring: true,
      enableBatteryMonitoring: true,
      enableDeviceInfo: true,
      enableDiskMonitoring: true,
      diskWarningThreshold: 85.0, // Warn at 85% disk usage
    ),
  );

  PerformanceMonitor.instance.cpuStream.listen((data) {
    cpuUsage = data.usage.toStringAsFixed(2);
  });

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Performance Pulse Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      builder: (context, child) {
        return Stack(
          children: [
            child!,
            const Positioned(
              right: 16,
              bottom: 16,
              child: Material(
                elevation: 8,
                borderRadius: BorderRadius.all(Radius.circular(8)),
                child: PerformanceDashboard(
                  showFPS: true,
                  showCPU: true,
                  showDisk: true, // Enable disk monitoring
                  theme: DashboardTheme(
                    backgroundColor: Color(0xFF1E1E1E),
                    textColor: Colors.white,
                    warningColor: Colors.orange,
                    errorColor: Colors.red,
                    chartLineColor: Colors.blue,
                    chartFillColor: Color(0x40808080),
                  ),
                ),
              ),
            ),
          ],
        );
      },
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _ramInfo = 'Loading...';
  String _cpuInfo = 'Loading...';
  String _diskInfo = 'Loading...';

  late Timer _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      _updateSystemInfo();
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  Future<void> _updateSystemInfo() async {
    // RAM Info
    final totalRam =
        SysInfo.getTotalPhysicalMemory() / (1024 * 1024 * 1024); // In GB
    final freeRam =
        SysInfo.getFreePhysicalMemory() / (1024 * 1024 * 1024); // In GB

    // CPU Info
    // For simplicity, let's assume a simple CPU usage calculation here, this can be replaced by platform-specific code
    // Disk Info
    DiskSpacePlus diskSpacePlus = DiskSpacePlus();
    final freeDiskSpace = await diskSpacePlus.getFreeDiskSpace;
    final totalSpace = await diskSpacePlus.getTotalDiskSpace;

    setState(() {
      _ramInfo =
          'Total RAM: ${totalRam.toStringAsFixed(2)} GB\nFree RAM: ${freeRam.toStringAsFixed(2)} GB';
      _cpuInfo = 'CPU Usage: ${cpuUsage}%';
      _diskInfo =
          'Total Storage: ${totalSpace?.toStringAsFixed(2)} GB\nFree Storage: ${freeDiskSpace?.toStringAsFixed(2)} GB';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('System Info with Real-time Performance'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(_ramInfo, style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 20),
            Text(_cpuInfo, style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 20),
            Text(_diskInfo, style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }
}
