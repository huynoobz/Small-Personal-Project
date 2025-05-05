import 'package:flutter/material.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:system_info/system_info.dart';
import 'package:disk_space_plus/disk_space_plus.dart';
import 'package:flutter_performance_pulse/flutter_performance_pulse.dart';
import 'dart:async';
import 'dart:io';

// This class is used to manage the device status and system information
class DeviceStatus {
  double totalRam = 0;
  double freeRam = 0;

  late DiskSpacePlus diskSpacePlus;
  double? totalDiskSpace = 0;
  double? freeDiskSpace = 0;

  String hardwareInfo = "0";

  double cpuUsage = 0;

  bool isInit = false;

  void init() async {
    if (isInit) {
      // Can only init once;
      return;
    }

    isInit = true;

    PerformanceMonitor.instance.cpuStream.listen((data) {
      cpuUsage = data.usage;
    });

    Timer.periodic(const Duration(seconds: 1), (timer) {
      updateStat();
    });
  }

  Future<void> updateStat() async {
    // Use platform-specific logic for RAM
    if (Platform.isAndroid || Platform.isIOS) {
      final deviceInfo = DeviceInfoPlugin();
      if (Platform.isAndroid) {
        final androidInfo = await deviceInfo.androidInfo;
        hardwareInfo = androidInfo.hardware;
        totalRam =
            SysInfo.getTotalPhysicalMemory() / (1024 * 1024 * 1024); // In GB
        freeRam =
            SysInfo.getFreeVirtualMemory() / (1024 * 1024 * 1024); // In GB
      } else if (Platform.isIOS) {
        final iosInfo = await deviceInfo.iosInfo;
        hardwareInfo = iosInfo.utsname.machine;
        // iOS does not provide direct RAM info, so set it to 0 or a default value
        totalRam = 0;
        freeRam = 0;
      }
    } else {
      hardwareInfo = "Unsupported OS";
      totalRam = 0;
      freeRam = 0;
    }

    // Disk space
    DiskSpacePlus diskSpacePlus = DiskSpacePlus();
    freeDiskSpace = (await diskSpacePlus.getFreeDiskSpace)! / (1024); // In GB
    totalDiskSpace = (await diskSpacePlus.getTotalDiskSpace)! / (1024); // In GB
  }

  double getTotalRam() => totalRam;
  double getFreeRam() => freeRam;

  String getHardwareInfo() => hardwareInfo;

  double getTotalDiskSpace() => totalDiskSpace ?? 0;
  double getFreeDiskSpace() => freeDiskSpace ?? 0;

  double getCpuUsage() => cpuUsage;
}

DeviceStatus deviceStatus = DeviceStatus();

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

  deviceStatus.init();

  runApp(const MyApp());
}

//// This is a simple Flutter application that displays system information such as RAM, CPU, and Disk Space
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

//// This widget displays the system information and updates it every second
class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

// State and UI of home
class _MyHomePageState extends State<MyHomePage> {
  String _ramInfo = 'Loading...';
  String _cpuInfo = 'Loading...';
  String _diskInfo = 'Loading...';
  String _hardwareInfo = 'Loading...';

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
    final totalRam = deviceStatus.totalRam; // In GB
    final freeRam = deviceStatus.freeRam; // In GB

    final hardwareInfo = deviceStatus.getHardwareInfo(); //Chip name

    final freeDiskSpace = deviceStatus.getFreeDiskSpace(); // In GB
    final totalSpace = deviceStatus.getTotalDiskSpace(); // In GB

    final cpuUsage = deviceStatus.getCpuUsage(); // In %

    setState(() {
      _ramInfo =
          'Total RAM: ${totalRam.toStringAsFixed(2)} GB\nFree RAM: ${freeRam.toStringAsFixed(2)} GB';
      _cpuInfo = 'CPU Usage: ${cpuUsage.toStringAsFixed(2)}%';
      _diskInfo =
          'Total Storage: ${totalSpace?.toStringAsFixed(2)} GB\nFree Storage: ${freeDiskSpace?.toStringAsFixed(2)} GB';
      _hardwareInfo = 'Cpu: ${hardwareInfo}';
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
            Text(_hardwareInfo, style: const TextStyle(fontSize: 18)),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}
