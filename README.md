# llama-android
- 안드로이드에서 TinyLlama.gguf를 로컬 구동해서 run 이후 웹뷰로 토큰을 생성될때마다 전송하는 프로그램
- 대화가 아닌 문장 완성형 LLM으로 프롬프트를 "로 시작하여 "로 끝내는 방식으로 작성하였기 때문에 출력 토큰에서 " 발견시 즉시 토큰 생성이 종료되도록 cpp 파일이 작성되었음
- 프롬프트는 assets에 존재
- llama_bridge.cpp와 react 웹뷰 프로젝트는 불포함
