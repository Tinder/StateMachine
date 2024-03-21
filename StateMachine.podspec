Pod::Spec.new do |s|
  s.name     = 'StateMachine'
  s.version  = '0.4.0'
  s.summary  = 'A state machine library in Swift'
  s.homepage = 'https://github.com/Tinder/StateMachine'
  s.license  = { :type => 'BSD 3-Clause "New" or "Revised" License', :file => 'LICENSE' }
  s.author   = { 'Tinder' => 'info@gotinder.com' }
  s.source   = { :git => 'https://github.com/Tinder/StateMachine.git', :tag => s.version }

  s.macos.deployment_target   = `make get-deployment-target platform=macos`
  s.ios.deployment_target     = `make get-deployment-target platform=ios`
  s.tvos.deployment_target    = `make get-deployment-target platform=tvos`
  s.watchos.deployment_target = `make get-deployment-target platform=watchos`

  s.swift_version = '5.2', '5.3', '5.4'
  s.source_files  = 'Swift/Sources/StateMachine/**/*'
end
