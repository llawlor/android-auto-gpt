#!/usr/bin/env python3
"""
Test script to debug Perplexity API requests without needing Android deployment.
This replicates the exact request format from the Android app.
"""

import json
import requests
import sys

def test_perplexity_api(api_key, test_message="What is 2+2?"):
    """Test the Perplexity API with the same format as the Android app"""
    
    # Exact same configuration as Android app
    url = "https://api.perplexity.ai/chat/completions"
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "User-Agent": "AndroidAutoVoiceAssistant/1.0"
    }
    
    # Exact same payload structure as Android app
    payload = {
        "model": "sonar",
        "messages": [
            {
                "role": "system", 
                "content": "You are a helpful AI assistant designed for use while driving. Keep responses concise, clear, and safe for audio consumption. Avoid long lists or complex formatting. Provide direct, actionable answers. Use web search when needed for current information."
            },
            {
                "role": "user",
                "content": test_message
            }
        ],
        "max_tokens": 150,
        "temperature": 0.7,
        "stream": False
    }
    
    print(f"🔑 API Key prefix: {api_key[:8]}...")
    print(f"📝 Test message: {test_message}")
    print(f"🌐 URL: {url}")
    print(f"🤖 Model: {payload['model']}")
    print(f"📦 Request payload:")
    print(json.dumps(payload, indent=2))
    print("\n" + "="*50)
    
    try:
        print("🚀 Making API request...")
        response = requests.post(url, headers=headers, json=payload, timeout=30)
        
        print(f"📊 Response status: {response.status_code}")
        print(f"📋 Response headers: {dict(response.headers)}")
        
        if response.status_code == 200:
            response_data = response.json()
            print(f"✅ Success! Response:")
            print(json.dumps(response_data, indent=2))
            
            # Extract the actual message
            if 'choices' in response_data and len(response_data['choices']) > 0:
                message = response_data['choices'][0]['message']['content']
                print(f"\n🎯 AI Response: {message}")
                return True, message
            else:
                print("❌ No choices in response")
                return False, "No choices in response"
                
        else:
            error_body = response.text
            print(f"❌ API Error {response.status_code}:")
            print(error_body)
            return False, f"HTTP {response.status_code}: {error_body}"
            
    except requests.exceptions.Timeout:
        error = "Request timed out after 30 seconds"
        print(f"⏰ {error}")
        return False, error
        
    except requests.exceptions.ConnectionError:
        error = "Connection error - check internet connection"
        print(f"🌐 {error}")
        return False, error
        
    except Exception as e:
        error = f"Unexpected error: {str(e)}"
        print(f"💥 {error}")
        return False, error

def main():
    print("🧪 Perplexity API Test Script")
    print("="*50)
    
    # Get API key
    api_key = input("Enter your Perplexity API key (pplx-...): ").strip()
    
    if not api_key.startswith("pplx-"):
        print("❌ API key should start with 'pplx-'")
        return
    
    # Test with simple question first
    print("\n🔬 Test 1: Simple math question")
    success, result = test_perplexity_api(api_key, "What is 2+2?")
    
    if success:
        print("\n🔬 Test 2: Web search question")
        success2, result2 = test_perplexity_api(api_key, "What's the weather like today?")
        
        if success2:
            print("\n✅ Both tests passed! Your Perplexity API is working correctly.")
            print("The issue might be in the Android app's network handling or response parsing.")
        else:
            print(f"\n⚠️  Simple test passed but web search failed: {result2}")
    else:
        print(f"\n❌ API test failed: {result}")
        print("\nCommon issues:")
        print("- Invalid API key")
        print("- API key not activated")
        print("- Network connectivity issues")
        print("- Model not available on your plan")

if __name__ == "__main__":
    main()
