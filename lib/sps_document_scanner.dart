import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

import 'ios_options.dart';

export 'ios_options.dart';

class CunningDocumentScanner {
  static const MethodChannel _channel =
      MethodChannel('cunning_document_scanner');

  /// Call this to start get Picture workflow.
  static Future<List<String>?> getPictures({
    int noOfPages = 100,
    bool isGalleryImportAllowed = false,
    IosScannerOptions? iosScannerOptions,
  }) async {
    // Request permissions based on platform version
    if (Platform.isAndroid) {
      if (await Permission.storage.status.isDenied) {
        await Permission.storage.request();
      }
      if (await Permission.camera.status.isDenied) {
        await Permission.camera.request();
      }
      // For Android 13 and above
      if (await Permission.photos.status.isDenied) {
        await Permission.photos.request();
      }
    }

    Map<Permission, PermissionStatus> statuses = await requestPermissions();
    // await [
    //   Permission.camera,
    //   if (Platform.isAndroid) ...[
    //     Permission.storage,
    //     Permission.photos,
    //   ],
    // ].request();
    

    if (statuses.containsValue(PermissionStatus.denied) ||
        statuses.containsValue(PermissionStatus.permanentlyDenied)) {
      throw Exception("Permission not granted");
    }

    final List<dynamic>? pictures = await _channel.invokeMethod('getPictures', {
      'noOfPages': noOfPages,
      'isGalleryImportAllowed': isGalleryImportAllowed,
      if (iosScannerOptions != null)
        'iosScannerOptions': {
          'imageFormat': iosScannerOptions.imageFormat.name,
          'jpgCompressionQuality': iosScannerOptions.jpgCompressionQuality,
        }
    });
    return pictures?.map((e) => e as String).toList();
  }

  static Future<Map<Permission, PermissionStatus>> requestPermissions() async {
    if (Platform.isAndroid) {
      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt >= 33) {
        return await [
          Permission.camera,
          Permission.photos,
          Permission.videos,
        ].request();
      } else {
        return await [
          Permission.camera,
          Permission.storage,
        ].request();
      }
    } else {
      return await [
        Permission.camera,
        Permission.photos,
      ].request();
    }
  }}
