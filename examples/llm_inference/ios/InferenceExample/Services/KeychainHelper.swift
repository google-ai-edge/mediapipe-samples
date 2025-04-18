// Copyright 2025 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import Foundation
import Security

/// Encapsulates reading, writing and deleting from Keychain.
struct KeychainHelper {
  /// Saves the value to the key.
  static func save(key: String, value: String) -> Bool {
    guard let data = value.data(using: .utf8) else {
      return false
    }

    _ = delete(key: key)

    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
      kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
    ]

    guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else {
      return false
    }
    
    return true
  }

  /// Returns the value of the key if present.
  static func load(key: String) -> String? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
      kSecReturnData as String: kCFBooleanTrue!,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]

    var result: AnyObject?
    _ = SecItemCopyMatching(query as CFDictionary, &result)

    guard let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
      return nil
    }
    
    return value
  }

  /// Deletes a key.
  static func delete(key: String) -> Bool {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
    ]

    let status = SecItemDelete(query as CFDictionary)
    
    return status == errSecSuccess || status == errSecItemNotFound
  }

  
  static func clear(keys: [String]) {
    for key in keys {
      guard !key.isEmpty else {
        return
      }
      _ = KeychainHelper.delete(key: key)
    }
  }
}

